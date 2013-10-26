/*
 *	Drifting Souls 2
 *	Copyright (c) 2006 Christopher Jung
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.driftingsouls.ds2.server.modules;

import net.driftingsouls.ds2.server.ContextCommon;
import net.driftingsouls.ds2.server.config.Medals;
import net.driftingsouls.ds2.server.config.Rang;
import net.driftingsouls.ds2.server.entities.ComNetChannel;
import net.driftingsouls.ds2.server.entities.ComNetEntry;
import net.driftingsouls.ds2.server.entities.ComNetVisit;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.bbcode.Smilie;
import net.driftingsouls.ds2.server.framework.pipeline.Module;
import net.driftingsouls.ds2.server.framework.pipeline.generators.Action;
import net.driftingsouls.ds2.server.framework.pipeline.generators.ActionType;
import net.driftingsouls.ds2.server.framework.pipeline.generators.TemplateGenerator;
import net.driftingsouls.ds2.server.framework.pipeline.generators.ValidierungException;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.Session;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Das ComNet - Alle Funktionalitaeten des ComNets befinden sich in
 * dieser Klasse.
 *
 * @author Christopher Jung
 */
@Module(name = "comnet")
public class ComNetController extends TemplateGenerator
{
	private static final Log log = LogFactory.getLog(ComNetController.class);

	/**
	 * Konstruktor.
	 *
	 * @param context Der zu verwendende Kontext
	 */
	public ComNetController(Context context)
	{
		super(context);

		setTemplate("comnet.html");

		setPageTitle("Com-Net");
	}

	private void validiereComNetChannel(ComNetChannel comNetChannel)
	{
		if (comNetChannel == null)
		{
			throw new ValidierungException("Die angegebene Frequenz existiert nicht");
		}
	}

	/**
	 * Sucht im aktuell ausgewaehlten ComNet-Kanal Posts nach bestimmten Kriterien.
	 * Sollten keine Kriterien angegeben sein, so wird das Eingabefenster fuer die Suche angezeigt.
	 *
	 * @param searchtype der Suchmodus.
	 * 1 - Suchen nach Teilen eines Titels
	 * 2 - Suchen nach Teilen eines Posts
	 * 3 - Suchen nach Posts eines bestimmten Spielers auf Basis der Spieler-ID
	 * @param search Der Suchbegriff, abhaengig vom Suchmodus
	 * @param back Der Offset der anzuzeigenden Posts. Ein Offset
	 * von 0 bedeutet der neuste Post. Je groesser der Wert umso aelter der Post
	 */
	@Action(ActionType.DEFAULT)
	public void searchAction(ComNetChannel channel, String search, int searchtype, int back)
	{
		validiereComNetChannel(channel);

		TemplateEngine t = getTemplateEngine();
		Session db = getContext().getDB();
		User user = (User) getUser();

		t.setVar("channel.id", channel.getId(),
				"channel.name", Common._title(channel.getName()));

		final int SCAN_TITLE = 1;
		final int SCAN_CONTENT = 2;
		final int SCAN_ID = 3;

		if (back < 0)
		{
			back = 0;
		}

		if (searchtype == 0)
		{
			t.setVar("show.searchform", 1);
			return;
		}

		t.setVar("show.read", 1);
		if (!channel.isReadable(user, this))
		{
			addError("Sie sind nicht berechtigt diese Frequenz zu empfangen", Common.buildUrl("default", "channel", channel.getId()));
			setTemplate("");

			return;
		}

		t.setVar("posts.action", "search",
				"search.string", search,
				"search.type", searchtype);

		Object searchArgument;
		if (searchtype == SCAN_ID)
		{
			try
			{
				searchArgument = Integer.valueOf(search);
			}
			catch (NumberFormatException e)
			{
				t.setVar("show.searchform", 1);
				addError("'" + search + "' ist keine g&uuml;ltige User-ID");
				return;
			}
		}
		else
		{
			searchArgument = "%" + search + "%";
		}

		if (channel.isWriteable(user, this))
		{
			t.setVar("channel.writeable", 1);
		}

		ComNetVisit visit = (ComNetVisit) db.createQuery("from ComNetVisit where user=:user and channel=:channel")
				.setParameter("user", user)
				.setParameter("channel", channel)
				.uniqueResult();
		visit.setTime(Common.time());

		Query query = null;
		switch (searchtype)
		{
			case SCAN_TITLE:
				query = db.createQuery("from ComNetEntry entry where entry.head like :input and channel=:channel order by entry.post desc");
				break;
			case SCAN_CONTENT:
				query = db.createQuery("from ComNetEntry entry where entry.text like :input and channel=:channel order by entry.post desc");
				break;
			case SCAN_ID:
				query = db.createQuery("from ComNetEntry entry where entry.user.id=:input and channel=:channel order by entry.post desc");
				break;
		}

		if (query != null)
		{
			List<ComNetEntry> entries = Common.cast(query.setParameter("input", searchArgument)
					.setParameter("channel", channel)
					.setFirstResult(back)
					.setMaxResults(10)
					.list());

			if (entries.isEmpty())
			{
				t.setVar("show.read", 0);
				t.setVar("show.searcherror", 1);
			}

			int channelPostCount = entries.size();

			int b = back + 10;
			int v = back - 10;
			if (b > channelPostCount)
			{
				b = 0;
			}

			t.setVar("show.vor", v,
					"show.back", b);

			if (back > 0)
			{
				t.setVar("read.nextpossible", 1);
			}

			//t.setVar("posts.action","read");
			t.setBlock("_COMNET", "posts.listitem", "posts.list");

			for (ComNetEntry entry : entries)
			{
				String head = entry.getHead();
				if (head.trim().isEmpty())
				{
					head = "-";
				}
				else
				{
					head = Common._title(head);
				}

				String text = Smilie.parseSmilies(Common._text(entry.getText()));

				t.setVar("post.pic", entry.getPic(),
						"post.postid", entry.getPost(),
						"post.id", entry.getUser().getId(),
						"post.name", Common._title(entry.getName()),
						"post.time", Common.date("d.m.Y H:i:s", entry.getTime()),
						"post.title", head,
						"post.text", text,
						"post.allypic", entry.getAllyPic(),
						"post.ingametime", Common.getIngameTime(entry.getTick()));

				t.parse("posts.list", "posts.listitem", true);
				t.stop_record();
				t.clear_record();
			}
		}
	}

	/**
	 * Zeigt den Inhalt des ausgewaehlten ComNet-Kanals an.
	 * Es werden immer nur 10 Posts ab einem angegebenen Offset angezeigt.
	 *
	 * @param back Der Offset der anzuzeigenden Posts. Ein Offset
	 * von 0 bedeutet der neuste Post. Je groesser der Wert umso aelter der Post
	 */
	@Action(ActionType.DEFAULT)
	public void readAction(ComNetChannel channel, int back)
	{
		validiereComNetChannel(channel);

		TemplateEngine t = getTemplateEngine();
		org.hibernate.Session db = getDB();
		User user = (User) getUser();

		t.setVar("channel.id", channel.getId(),
				"channel.name", Common._title(channel.getName()));

		t.setVar("show.read", 1);
		if (!channel.isReadable(user, this))
		{
			addError("Sie sind nicht berechtigt diesee Frequenz zu empfangen", Common.buildUrl("default", "channel", channel.getId()));
			setTemplate("");

			return;
		}

		if (channel.isWriteable(user, this))
		{
			t.setVar("channel.writeable", 1);
		}

		db.createQuery("update ComNetVisit set time= :time where user= :user and channel= :channel")
				.setLong("time", Common.time())
				.setEntity("user", user)
				.setEntity("channel", channel)
				.executeUpdate();

		if (back < 0)
		{
			back = 0;
		}

		int channelPostCount = channel.getPostCount();

		int b = back + 10;
		int v = back - 10;

		if (b > channelPostCount)
		{
			b = 0;
		}

		t.setVar("show.vor", v,
				"show.back", b);

		if (back > 0)
		{
			t.setVar("read.nextpossible", 1);
		}

		t.setVar("posts.action", "read");

		t.setBlock("_COMNET", "posts.listitem", "posts.list");

		int i = 0;

		List<?> postList = db.createQuery("from ComNetEntry where channel= :channel order by post desc")
				.setEntity("channel", channel)
				.setFirstResult(back)
				.setMaxResults(10)
				.list();
		for (Object aPostList : postList)
		{
			ComNetEntry post = (ComNetEntry) aPostList;

			t.start_record();
			int postNumber = channelPostCount - back - i;
			String head = post.getHead();
			String text = post.getText();

			text = Smilie.parseSmilies(Common._text(text));

			if (head.length() == 0)
			{
				head = "-";
			}
			else
			{
				head = Common._title(head);
			}

			t.setVar("post", post,
					"post.user.rang.name", Medals.get().rang(post.getUser().getRang()).getName(),
					"post.postid", postNumber,
					"post.name", Common._title(post.getName()),
					"post.time", Common.date("d.m.Y H:i:s", post.getTime()),
					"post.title", head,
					"post.text", text,
					"post.ingametime", Common.getIngameTime(post.getTick()));

			i++;

			t.parse("posts.list", "posts.listitem", true);
			t.stop_record();
			t.clear_record();
		}
	}

	/**
	 * Postet einen ComNet-Post im aktuell ausgewaehlten ComNet-Kanal.
	 *
	 * @param text Der Text des Posts
	 * @param head Der Titel des Posts
	 */
	@Action(ActionType.DEFAULT)
	public void sendenAction(ComNetChannel channel, String text, String head)
	{
		validiereComNetChannel(channel);

		User user = (User) getUser();
		TemplateEngine t = getTemplateEngine();
		org.hibernate.Session db = getDB();

		t.setVar("channel.id", channel.getId(),
				"channel.name", Common._title(channel.getName()));

		if (!channel.isWriteable(user, this))
		{
			addError("Sie sind nicht berechtigt auf dieser Frequenz zu senden", Common.buildUrl("default", "channel", channel));
			setTemplate("");

			return;
		}

		//In die DB eintragen
		ComNetEntry entry = new ComNetEntry(user, channel);
		entry.setHead(head);
		entry.setText(text);
		db.persist(entry);

		t.setVar("show.submit", 1);
	}

	/**
	 * Zeigt die Seite zum Verfassen eines neuen ComNet-Posts, im aktuell
	 * ausgewaehlten ComNet-Kanal, an.
	 */
	@Action(ActionType.DEFAULT)
	public void writeAction(ComNetChannel channel)
	{
		validiereComNetChannel(channel);

		User user = (User) getUser();
		TemplateEngine t = getTemplateEngine();

		t.setVar("channel.id", channel.getId(),
				"channel.name", Common._title(channel.getName()));

		if (!channel.isWriteable(user, this))
		{
			addError("Sie sind nicht berechtigt auf dieser Frequenz zu senden", Common.buildUrl("default", "channel", channel.getId()));
			setTemplate("");

			return;
		}

		t.setVar("show.inputform", 1,
				"post.raw.title", "",
				"post.raw.text", "");
	}

	/**
	 * Zeigt eine Vorschau fuer einen geschriebenen, jedoch noch nicht geposteten, ComNet-Post an.
	 * Nach einer Vorschau kann der Post im aktuell ausgewaehlten ComNet-Kanal gepostet werden.
	 *
	 * @param text Der Text des Posts
	 * @param head Der Titel des Posts
	 */
	@Action(ActionType.DEFAULT)
	public void vorschauAction(ComNetChannel channel, String text, String head)
	{
		validiereComNetChannel(channel);

		User user = (User) getUser();
		TemplateEngine t = getTemplateEngine();

		t.setVar("channel.id", channel.getId(),
				"channel.name", Common._title(channel.getName()));

		if (!channel.isWriteable(user, this))
		{
			addError("Sie sind nicht berechtigt auf dieser Frequenz zu senden", Common.buildUrl("default", "channel", channel.getId()));
			setTemplate("");

			return;
		}

		String tmpText = Smilie.parseSmilies(Common._text(text));
		String tmpHead = Common._title(head);

		//Aktuellen Tick ermitteln
		int tick = getContext().get(ContextCommon.class).getTick();

		Rang userRank = Medals.get().rang(user.getRang());
		String userRankName = "";
		if (userRank != null)
		{
			userRankName = userRank.getName();
		}
		else
		{
			log.debug("Illegal user rank for user " + user.getId() + " rankid: " + user.getRang());
		}

		t.setVar("show.vorschau", 1,
				"show.inputform", 1,
				"post.title", tmpHead,
				"post.text", tmpText,
				"post.raw.title", head,
				"post.raw.text", text,
				"post.postid", 1,
				"user", user,
				"user.rang.name", userRankName,
				"post.name", Common._title(user.getName()),
				"post.id", user.getId(),
				"post.pic", user.getId(),
				"post.allypic", user.getAlly() != null ? user.getAlly().getId() : 0,
				"post.time", Common.date("Y-m-d H:i:s"),
				"post.ingametime", Common.getIngameTime(tick));
	}

	/**
	 * Zeigt die Liste aller lesbaren ComNet-Kanaele an.
	 */
	@Action(ActionType.DEFAULT)
	public void defaultAction(ComNetChannel channel)
	{
		TemplateEngine t = getTemplateEngine();
		org.hibernate.Session db = getDB();
		User user = (User) getUser();

		if (channel == null)
		{
			channel = (ComNetChannel) db.get(ComNetChannel.class, 1);
		}

		t.setVar("channel.id", channel.getId(),
				"channel.name", Common._title(channel.getName()));

		t.setVar("show.channellist", 1);

		if (channel.isWriteable(user, this))
		{
			t.setVar("channel.writeable", 1);
		}

		if (channel.isReadable(user, this))
		{
			t.setVar("channel.readable", 1);
		}

		// Letzte "Besuche" auslesen
		Map<ComNetChannel, ComNetVisit> visits = new HashMap<>();

		List<?> visitList = db.createQuery("from ComNetVisit where user= :user")
				.setEntity("user", user)
				.list();
		for (Object aVisitList : visitList)
		{
			ComNetVisit avisit = (ComNetVisit) aVisitList;
			visits.put(avisit.getChannel(), avisit);
		}

		t.setBlock("_COMNET", "channels.listitem", "channels.list");

		int lastowner = 0;

		Iterator<?> chnlIter = db.createQuery("from ComNetChannel order by allyOwner").iterate();
		while (chnlIter.hasNext())
		{
			ComNetChannel achannel = (ComNetChannel) chnlIter.next();

			t.start_record();

			if (!achannel.isReadable(user, this))
			{
				continue;
			}

			t.setVar("thischannel.readable", 1);


			if (achannel.isWriteable(user, this))
			{
				t.setVar("thischannel.writeable", 1);
			}

			if ((lastowner == 0) && (lastowner != achannel.getAllyOwner()))
			{
				t.setVar("thischannel.showprivateinfo", 1);
				lastowner = achannel.getAllyOwner();
			}

			ComNetVisit visit = visits.get(achannel);

			if (visit == null)
			{
				visit = new ComNetVisit(user, achannel);
				visit.setTime(0);
				db.persist(visit);
			}

			t.setVar("thischannel.id", achannel.getId(),
					"thischannel.name", Common._title(achannel.getName()));

			Long lastpost = (Long) db.createQuery("select max(time) from ComNetEntry where channel= :channel")
					.setEntity("channel", achannel)
					.iterate().next();

			if (lastpost == null)
			{
				lastpost = 0L;
			}

			if (achannel.getId() == channel.getId())
			{
				t.setVar("thischannel.isactive", 1);
			}

			if (lastpost > visit.getTime())
			{
				t.setVar("thischannel.newposts", 1);
			}

			t.parse("channels.list", "channels.listitem", true);
			t.stop_record();
			t.clear_record();
		}
	}
}
