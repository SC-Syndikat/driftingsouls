package net.driftingsouls.ds2.server.framework.db;

import net.driftingsouls.ds2.server.DBTest;
import net.driftingsouls.ds2.server.config.StarSystem;
import org.junit.Test;

import javax.persistence.EntityManager;
import java.sql.SQLException;

import static net.driftingsouls.ds2.server.entities.jooq.Tables.SYSTEMS;
import static org.junit.Assert.assertEquals;

/**
 * jOOQ has to run inside the request transaction (docs/adr/0002): it must see what Hibernate has
 * flushed, and a rollback must undo jOOQ writes as well as Hibernate writes.
 */
public class DBUtilTest extends DBTest
{
	@Test
	public void gegebenEinGeflushterHibernateInsert_jooqSelect_sollteDieZeileSehen() throws SQLException
	{
		EntityManager em = getEM();
		em.getTransaction().begin();
		try
		{
			StarSystem sys = persist(new StarSystem());
			sys.setName("Transaktionstest");
			em.flush();

			assertEquals("Transaktionstest", readName(em, sys.getID()));
		}
		finally
		{
			em.getTransaction().rollback();
		}
	}

	@Test
	public void gegebenEinJooqUpdate_hibernateQueryInDerselbenTransaktion_sollteDenNeuenWertSehen() throws SQLException
	{
		EntityManager em = getEM();
		em.getTransaction().begin();
		try
		{
			StarSystem sys = persist(new StarSystem());
			sys.setName("vorher");
			em.flush();

			updateName(em, sys.getID(), "nachher");

			Object name = em.createNativeQuery("select name from systems where id = :id")
					.setParameter("id", sys.getID())
					.getSingleResult();
			assertEquals("nachher", name);
		}
		finally
		{
			em.getTransaction().rollback();
		}
	}

	@Test
	public void gegebenEinRollback_sollteJooqUndHibernateSchreibzugriffeZuruecknehmen() throws SQLException
	{
		EntityManager em = getEM();

		em.getTransaction().begin();
		StarSystem sys = persist(new StarSystem());
		sys.setName("committed");
		em.getTransaction().commit();
		int id = sys.getID();

		try
		{
			em.getTransaction().begin();
			updateName(em, id, "per jOOQ geaendert");
			StarSystem hibernateCreated = persist(new StarSystem());
			em.flush();
			int rolledBackId = hibernateCreated.getID();
			em.getTransaction().rollback();
			em.clear();

			em.getTransaction().begin();
			assertEquals("committed", readName(em, id));
			assertEquals(0, countSystems(em, rolledBackId));
			em.getTransaction().commit();
		}
		finally
		{
			if (em.getTransaction().isActive())
			{
				em.getTransaction().rollback();
			}
			em.getTransaction().begin();
			em.createNativeQuery("delete from systems where id = :id").setParameter("id", id).executeUpdate();
			em.getTransaction().commit();
		}
	}

	private static String readName(EntityManager em, int id) throws SQLException
	{
		try (var conn = DBUtil.getConnection(em))
		{
			return DBUtil.getDSLContext(conn)
					.select(SYSTEMS.NAME)
					.from(SYSTEMS)
					.where(SYSTEMS.ID.eq(id))
					.fetchOne(SYSTEMS.NAME);
		}
	}

	private static int countSystems(EntityManager em, int id) throws SQLException
	{
		try (var conn = DBUtil.getConnection(em))
		{
			return DBUtil.getDSLContext(conn).fetchCount(SYSTEMS, SYSTEMS.ID.eq(id));
		}
	}

	private static void updateName(EntityManager em, int id, String name) throws SQLException
	{
		try (var conn = DBUtil.getConnection(em))
		{
			DBUtil.getDSLContext(conn)
					.update(SYSTEMS)
					.set(SYSTEMS.NAME, name)
					.where(SYSTEMS.ID.eq(id))
					.execute();
		}
	}
}
