// Copyright (c) 2003-2013, Jodd Team (jodd.org). All Rights Reserved.

package jodd.db.oom;

import jodd.db.DbHsqldbTestCase;
import jodd.db.DbSession;
import jodd.db.DbThreadSession;
import jodd.db.oom.sqlgen.DbEntitySql;
import jodd.db.oom.tst.Boy;
import jodd.db.oom.tst.Girl2;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static jodd.db.oom.sqlgen.DbSqlBuilder.sql;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EntityCacheTest extends DbHsqldbTestCase {

	public static final String TSQL =
			"select $C{g.id, g.name, g.speciality}, $C{b.*} from " +
			"$T{Girl2 g} join $T{Boy b} on $g.id = $b.girlId " +
			"order by $g.id desc";

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		DbOomManager.resetAll();
		DbOomManager dbOom = DbOomManager.getInstance();
		dbOom.registerEntity(Girl2.class);
		dbOom.registerEntity(Boy.class);
	}

	@Test
	@SuppressWarnings("SimplifiableJUnitAssertion")
	public void testJoin() {
		DbSession session = new DbThreadSession(cp);

		assertEquals(1, DbEntitySql.insert(new Girl2(1, "Anna", "seduction")).query().executeUpdateAndClose());
		assertEquals(1, DbEntitySql.insert(new Girl2(2, "Sandra", "spying")).query().executeUpdateAndClose());
		assertEquals(1, DbEntitySql.insert(new Boy(1, "Johny", 2)).query().executeUpdateAndClose());
		assertEquals(1, DbEntitySql.insert(new Boy(2, "Marco", 2)).query().executeUpdateAndClose());
		assertEquals(1, DbEntitySql.insert(new Boy(3, "Hugo", 1)).query().executeUpdateAndClose());

		// map rows to two types, use cache, no hints

		DbOomQuery q = new DbOomQuery(sql(TSQL));

		List<Object[]> result = q.cacheEntities(true).listAndClose(Girl2.class, Boy.class);

		assertEquals(3, result.size());

		Girl2 girl1 = (Girl2) result.get(0)[0];
		Girl2 girl2 = (Girl2) result.get(1)[0];
		Girl2 girl3 = (Girl2) result.get(2)[0];

		assertTrue(girl1.equals(girl2));
		assertTrue(girl1 == girl2);
		assertFalse(girl3 == girl1);

		Boy boy1 = (Boy) result.get(0)[1];
		Boy boy2 = (Boy) result.get(1)[1];
		Boy boy3 = (Boy) result.get(2)[1];

		assertTrue(boy1.id != boy2.id);
		assertFalse(boy1 == boy2);
		assertFalse(boy2 == boy3);

		assertNull(girl1.getBoys());
		assertNull(girl3.getBoys());


		// map rows to two types, use cache and hints, but only one entity per row is stored in list

		q = new DbOomQuery(sql(TSQL));

		List<Girl2> result2 = q.withHints("g", "g.boys").cacheEntities(true).listAndClose(Girl2.class, Boy.class);

		assertEquals(3, result2.size());

		girl1 = result2.get(0);
		girl2 = result2.get(1);
		girl3 = result2.get(2);

		assertTrue(girl1.equals(girl2));
		assertTrue(girl1 == girl2);
		assertFalse(girl3 == girl1);

		assertNotNull(girl1.getBoys());
		assertEquals(2, girl1.getBoys().size());

		assertNotNull(girl3.getBoys());
		assertEquals(1, girl3.getBoys().size());
		assertEquals("Hugo", girl3.getBoys().get(0).name);


		// smart mode, same as above, except no duplicates in the list (entityAware mode)!

		q = new DbOomQuery(sql(TSQL));

		result2 = q.withHints("g", "g.boys").entityAwareMode(true).listAndClose(Girl2.class, Boy.class);

		assertEquals(2, result2.size());

		girl1 = result2.get(0);
		girl3 = result2.get(1);

		assertNotNull(girl1.getBoys());
		assertEquals(2, girl1.getBoys().size());

		assertNotNull(girl3.getBoys());
		assertEquals(1, girl3.getBoys().size());


		// enetytAware with list + max

		q = new DbOomQuery(sql(TSQL));

		result2 = q.withHints("g", "g.boys").entityAwareMode(true).listAndClose(1, Girl2.class, Boy.class);

		assertEquals(1, result2.size());

		girl1 = result2.get(0);

		assertNotNull(girl1.getBoys());
		assertEquals(2, girl1.getBoys().size());


		// entityAware with set

		q = new DbOomQuery(sql(TSQL));

		Set<Girl2> set1 = q.withHints("g", "g.boys").entityAwareMode(true).listSetAndClose(Girl2.class, Boy.class);

		assertEquals(2, set1.size());

		for (Girl2 girl : set1) {
			if (girl.id.equals(1)) {
				assertEquals(1, girl.getBoys().size());
			}
			if (girl.id.equals(2)) {
				assertEquals(2, girl.getBoys().size());
			}
		}

		// entityAware with set + max

		q = new DbOomQuery(sql(TSQL));

		set1 = q.withHints("g", "g.boys").entityAwareMode(true).listSetAndClose(1, Girl2.class, Boy.class);

		assertEquals(1, set1.size());

		for (Girl2 girl : set1) {
			if (girl.id.equals(2)) {
				assertEquals(2, girl.getBoys().size());
			} else {
				fail();
			}
		}

		// iterator

		q = new DbOomQuery(sql(TSQL));

		Iterator<Girl2> iterator = q.withHints("g", "g.boys").entityAwareMode(true).iterateAndClose(Girl2.class, Boy.class);

		assertTrue(iterator.hasNext());

		girl1 = iterator.next();

		assertNotNull(girl1.getBoys());
		assertEquals(2, girl1.getBoys().size());

		assertTrue(iterator.hasNext());

		girl3 = iterator.next();

		assertNotNull(girl3.getBoys());
		assertEquals(1, girl3.getBoys().size());

		assertFalse(iterator.hasNext());

		// ---------------------------------------------------------------- find

		q = new DbOomQuery(sql(TSQL));

		girl1 = q.withHints("g", "g.boys").entityAwareMode(true).findAndClose(Girl2.class, Boy.class);

		assertNotNull(girl1.getBoys());
		assertEquals(2, girl1.getBoys().size());

		// the end

		session.closeSession();
	}

}