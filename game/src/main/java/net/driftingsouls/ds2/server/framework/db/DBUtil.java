package net.driftingsouls.ds2.server.framework.db;

import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.service.jdbc.connections.spi.ConnectionProvider;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

import javax.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;

public class DBUtil {
    /**
     * Returns a connection for jOOQ. While the EntityManager has an active transaction, this is the
     * session's own connection, so jOOQ reads and writes take part in that transaction: they see rows
     * Hibernate has already flushed, and a rollback undoes both. Pending Hibernate changes are still
     * only visible after an explicit {@code em.flush()} (see docs/adr/0005).
     *
     * <p>Callers close the returned connection (try-with-resources). For the session connection this
     * closes only Hibernate's proxy, not the underlying connection, which stays with the session until
     * the transaction ends.
     *
     * <p>Without an active transaction a separate pool connection is returned, as before.
     */
    public static Connection getConnection(EntityManager em) throws SQLException {
        Session session = em.unwrap(Session.class);
        if (em.getTransaction().isActive()) {
            return ((SessionImplementor) session).connection();
        }

        SessionFactoryImplementor sfi = (SessionFactoryImplementor) session.getSessionFactory();
        @SuppressWarnings("deprecation")
        ConnectionProvider cp = sfi.getConnectionProvider();
        HikariDataSource ds = cp.unwrap(HikariDataSource.class);

        return ds.getConnection();
    }

    public static DSLContext getDSLContext(Connection conn) throws SQLException {
        //Write sql without schema, e.g. SELECT * FROM ships, not SELECT * FROM ds.ships
        var settings = new Settings()
            .withRenderSchema(false);
        return DSL.using(conn, SQLDialect.MYSQL, settings).dsl();
    }
}
