package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.entities.User;

import javax.persistence.EntityManager;

/**
 * NPC accounts for tests. The game has no code that creates them (they have negative ids and are set up in
 * the database), so a new user is moved to a negative id.
 */
public final class NpcTestUsers
{
	private NpcTestUsers()
	{
		// static only
	}

	/**
	 * Creates the NPC before anything refers to it; call it first and clears the persistence context.
	 */
	public static User create(EntityManager em, String name, int npcId)
	{
		// New users get max(id) + 1: keep a regular user so that players created later stay positive
		Integer max = em.createQuery("SELECT max(id) from User", Integer.class).getSingleResult();
		if (max == null || max < 1)
		{
			new User("platzhalter", "***", 0, "", new Cargo(), "platzhalter@localhost");
		}
		User user = new User(name, "***", 0, "", new Cargo(), name + "@localhost");
		em.flush();
		int id = user.getId();
		em.clear();
		em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
		for (String update : new String[]{
				"UPDATE users SET id = :npc WHERE id = :id",
				"UPDATE user_relations SET user_id = :npc WHERE user_id = :id",
				"UPDATE user_relations SET target_id = :npc WHERE target_id = :id",
				"UPDATE ordner SET playerid = :npc WHERE playerid = :id"})
		{
			em.createNativeQuery(update).setParameter("npc", npcId).setParameter("id", id).executeUpdate();
		}
		em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
		return em.find(User.class, npcId);
	}
}
