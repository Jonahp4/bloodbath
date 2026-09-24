package net.minecraft.util;
public interface ActionResult {
	Success SUCCESS = null;
	Fail FAIL = null;
	Pass PASS = null;
	final class Success implements ActionResult {}
	final class Fail implements ActionResult {}
	final class Pass implements ActionResult {}
}
