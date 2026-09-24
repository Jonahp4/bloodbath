package net.minecraft.world;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
public interface EntityView {
	<T extends Entity> List<T> getEntitiesByClass(Class<T> entityClass, Box box, Predicate<? super T> predicate);
	List<?> getPlayers();
}
