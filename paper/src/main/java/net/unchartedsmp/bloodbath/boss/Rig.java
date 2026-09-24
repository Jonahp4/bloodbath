package net.unchartedsmp.bloodbath.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Blood Knight's skeleton, as exported by {@code tools/models/boss.py}: bones (parents listed
 * before children, each with its pivot relative to its parent) and parts (one item model each,
 * placed in a bone's frame). Immutable once loaded; one instance serves every boss.
 */
public final class Rig {
	public record Bone(String name, int parent, Vector3f pivot) {
	}

	public record Part(int bone, NamespacedKey model, Vector3f offset, Quaternionf rotation, float scale) {
	}

	private final List<Bone> bones;
	private final List<Part> parts;
	private final Map<String, Integer> index = new HashMap<>();

	private Rig(List<Bone> bones, List<Part> parts) {
		this.bones = List.copyOf(bones);
		this.parts = List.copyOf(parts);
		for (int i = 0; i < bones.size(); i++) {
			index.put(bones.get(i).name(), i);
		}
	}

	public static Rig load(InputStream in) throws IOException {
		if (in == null) {
			throw new IOException("the rig resource is missing from the plugin jar");
		}
		JsonObject json;
		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			json = JsonParser.parseReader(reader).getAsJsonObject();
		}
		List<Bone> bones = new ArrayList<>();
		Map<String, Integer> byName = new HashMap<>();
		for (JsonElement element : json.getAsJsonArray("bones")) {
			JsonObject bone = element.getAsJsonObject();
			String name = bone.get("name").getAsString();
			int parent = bone.get("parent").isJsonNull() ? -1 : byName.getOrDefault(bone.get("parent").getAsString(), -1);
			byName.put(name, bones.size());
			bones.add(new Bone(name, parent, vector(bone.getAsJsonArray("pivot"))));
		}
		List<Part> parts = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("parts")) {
			JsonObject part = element.getAsJsonObject();
			Integer bone = byName.get(part.get("bone").getAsString());
			NamespacedKey model = NamespacedKey.fromString(part.get("model").getAsString());
			if (bone == null || model == null) {
				throw new IOException("bad rig part " + part);
			}
			JsonArray q = part.getAsJsonArray("rotation");
			parts.add(new Part(bone, model, vector(part.getAsJsonArray("offset")),
				new Quaternionf(q.get(0).getAsFloat(), q.get(1).getAsFloat(), q.get(2).getAsFloat(), q.get(3).getAsFloat()).normalize(),
				part.get("scale").getAsFloat()));
		}
		return new Rig(bones, parts);
	}

	private static Vector3f vector(JsonArray a) {
		return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
	}

	public List<Bone> bones() {
		return bones;
	}

	public List<Part> parts() {
		return parts;
	}

	/** Bone index by name, or -1. */
	public int bone(String name) {
		return index.getOrDefault(name, -1);
	}
}
