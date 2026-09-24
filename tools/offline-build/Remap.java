import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Remaps classes compiled against the Yarn-named stubs to Fabric intermediary names (what a
 * production Fabric server runs), then verifies every Minecraft reference in the output against
 * the official 1.21.11 intermediary mappings. Any reference it can't prove exists fails the build.
 *
 * <pre>
 * java Remap &lt;yarnDir&gt; &lt;manual.mapping&gt; &lt;intermediary.tiny&gt; &lt;stubClasses&gt; &lt;inClasses&gt; &lt;outClasses&gt; [oracleRefs]
 * </pre>
 */
public final class Remap {
	private final Map<String, String> classMap = new HashMap<>();             // named -> intermediary
	private final Map<String, String> memberMap = new HashMap<>();            // interOwner#named#interDesc -> inter
	private final Map<String, List<String>> supers = new HashMap<>();         // named class -> supertypes
	private final Map<String, Set<String>> declared = new HashMap<>();        // named class -> name+desc
	private final Set<String> stubClasses = new HashSet<>();
	private final Set<String> yarnConstructors = new HashSet<>();           // interOwner + desc
	private final List<String> errors = new ArrayList<>();

	public static void main(String[] args) throws IOException {
		Remap remap = new Remap();
		remap.loadYarn(Paths.get(args[0]));
		remap.loadManual(Paths.get(args[1]));
		remap.indexClasses(Paths.get(args[3]), true);
		remap.indexClasses(Paths.get(args[4]), false);
		remap.remapAll(Paths.get(args[4]), Paths.get(args[5]));
		Set<String> oracle = args.length > 6 ? new HashSet<>(Files.readAllLines(Paths.get(args[6]))) : Set.of();
		remap.verify(Paths.get(args[2]), Paths.get(args[5]), oracle);
		if (!remap.errors.isEmpty()) {
			remap.errors.forEach(e -> System.err.println("ERROR: " + e));
			System.exit(1);
		}
		System.out.println("Remapped and verified OK");
	}

	// ---- mapping input ----------------------------------------------------------------------

	private void loadYarn(Path dir) throws IOException {
		List<Path> files;
		try (Stream<Path> s = Files.walk(dir)) {
			files = s.filter(p -> p.toString().endsWith(".mapping")).sorted().collect(Collectors.toList());
		}
		// Two passes: class names first so member descriptors can be translated.
		List<String[]> pendingMembers = new ArrayList<>();
		for (Path file : files) {
			Deque<String[]> stack = new ArrayDeque<>(); // {inter, named}
			for (String line : Files.readAllLines(file)) {
				if (line.isBlank()) continue;
				int depth = 0;
				while (depth < line.length() && line.charAt(depth) == '\t') depth++;
				String[] p = line.trim().split("\\s+");
				while (stack.size() > depth) stack.pop();
				switch (p[0]) {
					case "CLASS" -> {
						String inter = p[1];
						String named = p.length > 2 ? p[2] : p[1];
						if (depth > 0) {
							String[] outer = stack.peek();
							inter = outer[0] + "$" + inter;
							named = outer[1] + "$" + named;
						}
						classMap.put(named, inter);
						stack.push(new String[] {inter, named});
					}
					case "METHOD", "FIELD" -> {
						if (stack.size() != depth || stack.isEmpty()) break;
						String owner = stack.peek()[0];
						if (p.length == 4) pendingMembers.add(new String[] {owner, p[2], p[3], p[1]});
						else pendingMembers.add(new String[] {owner, p[1], p[2], p[1]});
					}
					default -> {
						// ARG / COMMENT: pushed so nesting depth stays aligned, never matched.
						stack.push(new String[] {"", ""});
					}
				}
			}
		}
		for (String[] m : pendingMembers) {
			if (m[1].equals("<init>")) yarnConstructors.add(m[0] + m[2]);
			else memberMap.put(m[0] + "#" + m[1] + "#" + m[2], m[3]);
		}
	}

	/** Lines: CLASS named inter | METHOD|FIELD namedOwner name namedDesc inter */
	private void loadManual(Path file) throws IOException {
		List<String[]> members = new ArrayList<>();
		for (String line : Files.readAllLines(file)) {
			line = line.strip();
			if (line.isEmpty() || line.startsWith("#")) continue;
			String[] p = line.split("\\s+");
			if (p[0].equals("CLASS")) classMap.put(p[1], p[2]);
			else members.add(p);
		}
		for (String[] p : members) {
			String owner = classMap.getOrDefault(p[1], p[1]);
			String desc = p[0].equals("METHOD") ? mapper.mapMethodDesc(p[3]) : mapper.mapDesc(p[3]);
			memberMap.put(owner + "#" + p[2] + "#" + desc, p[4]);
		}
	}

	private void indexClasses(Path dir, boolean stubs) throws IOException {
		forEachClass(dir, (path, bytes) -> new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
			String name;

			@Override
			public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
				this.name = name;
				List<String> s = new ArrayList<>();
				if (superName != null) s.add(superName);
				if (interfaces != null) s.addAll(Arrays.asList(interfaces));
				supers.put(name, s);
				declared.put(name, new HashSet<>());
				if (stubs) stubClasses.add(name);
			}

			@Override
			public FieldVisitor visitField(int access, String n, String d, String sig, Object value) {
				declared.get(name).add(n + ":" + d);
				return null;
			}

			@Override
			public MethodVisitor visitMethod(int access, String n, String d, String sig, String[] ex) {
				declared.get(name).add(n + d);
				return null;
			}
		}, ClassReader.SKIP_CODE));
	}

	// ---- remapping --------------------------------------------------------------------------

	private boolean isMinecraft(String owner) {
		return owner.startsWith("net/minecraft/");
	}

	private List<String> hierarchy(String owner) {
		List<String> out = new ArrayList<>();
		Deque<String> queue = new ArrayDeque<>(List.of(owner));
		Set<String> seen = new HashSet<>();
		while (!queue.isEmpty()) {
			String c = queue.poll();
			if (!seen.add(c)) continue;
			out.add(c);
			queue.addAll(supers.getOrDefault(c, List.of()));
		}
		return out;
	}

	private String resolve(String owner, String name, String namedDesc, String interDesc, boolean method) {
		List<String> chain = hierarchy(owner);
		for (String c : chain) {
			String hit = memberMap.get(classMap.getOrDefault(c, c) + "#" + name + "#" + interDesc);
			if (hit != null) return hit;
		}
		String key = method ? name + namedDesc : name + ":" + namedDesc;
		for (String c : chain) {
			if (isMinecraft(c) && stubClasses.contains(c) && declared.getOrDefault(c, Set.of()).contains(key)) {
				errors.add("No intermediary mapping for " + c + "." + name + " " + namedDesc);
				return name;
			}
		}
		return name; // our own member, or one inherited from the JDK
	}

	private final Remapper mapper = new Remapper() {
		@Override
		public String map(String internalName) {
			return classMap.getOrDefault(internalName, internalName);
		}

		@Override
		public String mapMethodName(String owner, String name, String descriptor) {
			if (name.startsWith("<") || owner.startsWith("[")) return name;
			return resolve(owner, name, descriptor, mapMethodDesc(descriptor), true);
		}

		@Override
		public String mapFieldName(String owner, String name, String descriptor) {
			return resolve(owner, name, descriptor, mapDesc(descriptor), false);
		}

		@Override
		public String mapInvokeDynamicMethodName(String name, String descriptor) {
			String iface = Type.getReturnType(descriptor).getInternalName();
			if (!isMinecraft(iface)) return name;
			String prefix = map(iface) + "#" + name + "#";
			Set<String> hits = memberMap.entrySet().stream()
				.filter(e -> e.getKey().startsWith(prefix)).map(Map.Entry::getValue).collect(Collectors.toSet());
			if (hits.size() == 1) return hits.iterator().next();
			if (hits.isEmpty()) errors.add("No intermediary mapping for lambda target " + iface + "." + name);
			else errors.add("Ambiguous lambda target " + iface + "." + name + " -> " + hits);
			return name;
		}
	};

	private void remapAll(Path in, Path out) throws IOException {
		forEachClass(in, (path, bytes) -> {
			ClassReader reader = new ClassReader(bytes);
			ClassWriter writer = new ClassWriter(0);
			reader.accept(new ClassRemapper(writer, mapper), 0);
			Path target = out.resolve(in.relativize(path));
			try {
				Files.createDirectories(target.getParent());
				Files.write(target, writer.toByteArray());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	// ---- verification -----------------------------------------------------------------------

	/**
	 * Checks every net/minecraft reference in the remapped output: the class must exist in the
	 * intermediary mappings, and each method/field name+descriptor must exist there too (or be an
	 * exact reference already used by known-good 1.21.11 mods, which covers constructors and
	 * members whose names aren't obfuscated). Constructors are checked against Yarn's 1.21.11 files.
	 */
	private void verify(Path tiny, Path out, Set<String> oracle) throws IOException {
		Map<String, String> obfToInter = new HashMap<>();
		List<String[]> rows = new ArrayList<>();
		for (String line : Files.readAllLines(tiny)) {
			String[] p = line.split("\t");
			if (p[0].equals("CLASS")) obfToInter.put(p[1], p[2]);
			else if (p[0].equals("METHOD") || p[0].equals("FIELD")) rows.add(p);
		}
		Set<String> classes = new HashSet<>(obfToInter.values());
		// Unobfuscated classes (e.g. MinecraftServer) have no CLASS line, only member rows.
		for (String[] p : rows) {
			classes.add(obfToInter.getOrDefault(p[1], p[1]));
		}
		Remapper obf = new Remapper() {
			@Override
			public String map(String name) {
				return obfToInter.getOrDefault(name, name);
			}
		};
		Set<String> members = new HashSet<>();
		for (String[] p : rows) {
			String desc = p[0].equals("METHOD") ? obf.mapMethodDesc(p[2]) : obf.mapDesc(p[2]);
			members.add(p[0].charAt(0) + " " + p[4] + " " + desc);
		}

		forEachClass(out, (path, bytes) -> new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
			String self;

			@Override
			public void visit(int v, int a, String name, String sig, String sup, String[] itf) {
				self = name;
				checkClass(sup);
				if (itf != null) for (String i : itf) checkClass(i);
			}

			void checkClass(String c) {
				if (c != null && isMinecraft(c) && !classes.contains(c)) errors.add(self + ": unknown class " + c);
			}

			void checkMember(char kind, String owner, String name, String desc) {
				if (!isMinecraft(owner)) return;
				checkClass(owner);
				String ref = kind + " " + owner + " " + name + " " + desc;
				if (oracle.contains(ref)) return;
				if (name.equals("<init>")) {
					if (yarnConstructors.contains(owner + desc)) return;
					errors.add(self + ": constructor not verifiable " + ref);
				} else if (!members.contains(kind + " " + name + " " + desc)) {
					errors.add(self + ": unknown member " + ref);
				}
			}

			@Override
			public MethodVisitor visitMethod(int access, String mname, String mdesc, String sig, String[] ex) {
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitFieldInsn(int op, String o, String n, String d) {
						checkMember('F', o, n, d);
					}

					@Override
					public void visitMethodInsn(int op, String o, String n, String d, boolean itf) {
						checkMember('M', o, n, d);
					}

					@Override
					public void visitTypeInsn(int op, String type) {
						checkClass(type);
					}
				};
			}
		}, 0));
	}

	private interface ClassConsumer {
		void accept(Path path, byte[] bytes) throws IOException;
	}

	private static void forEachClass(Path dir, ClassConsumer consumer) throws IOException {
		List<Path> files;
		try (Stream<Path> s = Files.walk(dir)) {
			files = s.filter(p -> p.toString().endsWith(".class")).sorted().collect(Collectors.toList());
		}
		for (Path p : files) consumer.accept(p, Files.readAllBytes(p));
	}
}
