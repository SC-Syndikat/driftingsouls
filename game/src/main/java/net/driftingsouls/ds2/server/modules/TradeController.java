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
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ResourceEntry;
import net.driftingsouls.ds2.server.cargo.ResourceList;
import net.driftingsouls.ds2.server.config.Faction;
import net.driftingsouls.ds2.server.entities.GtuWarenKurse;
import net.driftingsouls.ds2.server.entities.ResourceLimit;
import net.driftingsouls.ds2.server.entities.SellLimit;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserMoneyTransfer;
import net.driftingsouls.ds2.server.entities.WellKnownUserValue;
import net.driftingsouls.ds2.server.entities.statistik.StatVerkaeufe;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.bbcode.BBCodeParser;
import net.driftingsouls.ds2.server.framework.pipeline.Module;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.Action;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.ActionType;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.Controller;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.RedirectViewResult;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.UrlParam;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.ValidierungException;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import net.driftingsouls.ds2.server.framework.templates.TemplateViewResultFactory;
import net.driftingsouls.ds2.server.services.CargoService;
import net.driftingsouls.ds2.server.services.HandelspostenService;
import net.driftingsouls.ds2.server.services.LocationService;
import net.driftingsouls.ds2.server.services.PmService;
import net.driftingsouls.ds2.server.services.ShipActionService;
import net.driftingsouls.ds2.server.services.UserService;
import net.driftingsouls.ds2.server.services.UserValueService;
import net.driftingsouls.ds2.server.ships.Ship;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * Verkauft Waren an einem Handelsposten.
 *
 * @author Christopher Jung
 */
@Module(name = "trade")
public class TradeController extends Controller
{
	private static final Log log = LogFactory.getLog(TradeController.class);


	@PersistenceContext
	private EntityManager em;

	private final TemplateViewResultFactory templateViewResultFactory;
	private final PmService pmService;
	private final HandelspostenService tradingPostService;
	private final BBCodeParser bbCodeParser;
	private final UserValueService userValueService;
	private final LocationService locationService;
	private final UserService userService;
	private final CargoService cargoService;
	private final ShipActionService shipActionService;

	public TradeController(TemplateViewResultFactory templateViewResultFactory, PmService pmService, HandelspostenService tradingPostService, BBCodeParser bbCodeParser, UserValueService userValueService, LocationService locationService, UserService userService, CargoService cargoService, ShipActionService shipActionService)
	{
		this.templateViewResultFactory = templateViewResultFactory;
		this.pmService = pmService;
		this.bbCodeParser = bbCodeParser;
		this.userValueService = userValueService;
		this.locationService = locationService;
		this.userService = userService;
		this.cargoService = cargoService;
		this.shipActionService = shipActionService;

		setPageTitle("Handelsposten");
		this.tradingPostService = tradingPostService;
	}

	private void validiereSchiff(Ship ship)
	{
		if ((ship == null) || (ship.getId() < 0) || (ship.getOwner() != getUser()))
		{
			throw new ValidierungException("Fehler: Das angegebene Schiff existiert nicht oder gehört nicht Ihnen", Common.buildUrl("default", "module", "schiffe"));
		}
	}

	private void validiereHandelsposten(Ship handelndesSchiff, Ship handelsposten)
	{
		if (handelsposten == null || !handelsposten.isTradepost() || !handelsposten.getLocation().equals(handelndesSchiff.getLocation()))
		{
			throw new ValidierungException("Fehler: Der angegebene Handelsposten konnte nicht im Sektor lokalisiert werden", Common.buildUrl("default", "module", "schiff", "ship", handelndesSchiff.getId()));
		}

		User user = (User) getUser();
		if (!tradingPostService.isTradepostVisible(handelsposten, user))
		{
			throw new ValidierungException("Fehler: Dieser Handelsposten handelt nicht mit Ihnen. Für die Aufnahme von Handelsbeziehungen setzen Sie sich mit dem Eigner in Verbindung.", Common.buildUrl("default", "module", "schiff", "ship", handelndesSchiff.getId()));
		}
	}

	private GtuWarenKurse ermittleWarenKurseFuerHandelsposten(Ship handelndesSchiff, Ship handelsposten)
	{
		GtuWarenKurse kurse = em.find(GtuWarenKurse.class, "p" + handelsposten.getId());

		if (kurse == null && handelsposten.getOwner().getRace() == Faction.GTU_RASSE)
		{
			kurse = em.find(GtuWarenKurse.class, "tradepost");
		}
		if (kurse == null)
		{
			throw new ValidierungException("Fehler: Der An- und Verkauf auf dem Handelsposten wurde nicht freigegeben", Common.buildUrl("default", "module", "schiff", "ship", handelndesSchiff.getId()));
		}
		return kurse;
	}

	/**
	 * Kauft die angegebenen Waren vom Handelsposten.
	 *
	 * @param tradepost die ID des Handelspostens, an dem die Waren verkauft werden sollen
	 * @param ship die ID des Schiffes, das Waren verkaufen moechte
	 */
	@Action(ActionType.DEFAULT)
	public RedirectViewResult buyAction(@UrlParam(name = "#from") Map<String, Long> fromMap, Ship tradepost, Ship ship)
	{
		validiereSchiff(ship);
		validiereHandelsposten(ship, tradepost);

		ResourceList resourceList = tradepost.getCargo().getResourceList();
		Cargo tradepostCargo = tradepost.getCargo();
		User user = (User) getUser();
		BigInteger moneyOfBuyer = user.getKonto();
		BigInteger totalRE = BigInteger.ZERO;
		StringBuilder pmText = new StringBuilder(ship.getOwner().getName() + " kauft: \n");

		log.info("Warenkauf an HP " + tradepost.getId() + " durch Schiff " + ship.getId() + " [User: " + user.getId() + "]");

		for (ResourceEntry resource : resourceList)
		{
			Long amountToBuy = fromMap.get(resource.getId().toString());

			if (amountToBuy == null || amountToBuy <= 0)
			{
				continue;
			}

			//Preis und Minimum holen
			SellLimit limit = SellLimit.fuerSchiffUndItem(tradepost, resource.getId());

			//Ware wird nicht verkauft
			if (limit == null || limit.getPrice() <= 0)
			{
				continue;
			}

			//The seller lacks the rank needed to sell this resource
			if (!limit.willSell(tradepost.getOwner(), user))
			{
				continue;
			}

			long amountOnPost = tradepostCargo.getResourceCount(resource.getId()) - limit.getLimit();
			if (amountOnPost <= 0)
			{
				continue;
			}

			if (amountToBuy > amountOnPost)
			{
				amountToBuy = amountOnPost;
			}

			long resourceMass = Cargo.getResourceMass(resource.getId(), 1);
			long neededSpace = amountToBuy * resourceMass;
			long freeSpaceOnShip = ship.getMaxCargo() - cargoService.getMass(ship.getCargo());

			if (neededSpace > freeSpaceOnShip)
			{
				amountToBuy = freeSpaceOnShip / resourceMass;
			}

			long price = amountToBuy * limit.getPrice();
			//Nicht genug Geld da
			if (moneyOfBuyer.compareTo(BigInteger.valueOf(price)) < 0)
			{
				amountToBuy = moneyOfBuyer.divide(BigInteger.valueOf(price)).longValue();
				price = amountToBuy * limit.getPrice();
			}
			log.info("Verkaufe " + amountToBuy + "x " + resource.getId() + " fuer gesamt " + price);
			pmText.append("[resource=").append(resource.getId()).append("]").append(amountToBuy).append("[/resource] für ").append(price).append(" RE\n");
			totalRE = totalRE.add(BigInteger.valueOf(price));

			if (amountToBuy <= 0)
			{
				continue;
			}

			moneyOfBuyer = moneyOfBuyer.subtract(BigInteger.valueOf(price));

			tradepost.transfer(ship, resource.getId(), amountToBuy);


		}

		shipActionService.recalculateShipStatus(ship);
		shipActionService.recalculateShipStatus(tradepost);

		if (totalRE.compareTo(BigInteger.ZERO) > 0)
		{
			//Benachrichtigung fuer HP-Besitzer schreiben
			if(ship.getOwner().getId()!=tradepost.getOwner().getId() && userValueService.getUserValue(tradepost.getOwner(), WellKnownUserValue.GAMEPLAY_USER_HANDELSPOSTEN_PM))
			{
				pmService.send(tradepost.getOwner(), tradepost.getOwner().getId(), "Warenverkauf an "+tradepost.getName(), pmText.toString());
			}
			tradepost.getOwner()
					.transferMoneyFrom(user.getId(), totalRE,
							"Warenverkauf an "+tradepost.getName()+" bei " + locationService.displayCoordinates(tradepost.getLocation(), false),
							false, UserMoneyTransfer.Transfer.SEMIAUTO);
		}
		return new RedirectViewResult("default");
	}

	/**
	 * Verkauft die angegebenen Waren.
	 *  @param toMap Verkauft die Resource mit der ID (Key) in der angegebenen Menge
	 * @param tradepost die ID des Handelspostens, an dem die Waren verkauft werden sollen
	 * @param ship die ID des Schiffes, das Waren verkaufen moechte
	 */
	@Action(ActionType.DEFAULT)
	public RedirectViewResult sellAction(@UrlParam(name = "#to") Map<String, Long> toMap, Ship tradepost, Ship ship)
	{
		User user = (User) getUser();
		StringBuilder pmText = new StringBuilder(ship.getOwner().getName() + " verkauft:\n ");

		validiereSchiff(ship);
		validiereHandelsposten(ship, tradepost);

		GtuWarenKurse kurse = ermittleWarenKurseFuerHandelsposten(ship, tradepost);

		int MIN_TICKS_TO_SURVIVE = 7;
		Cargo shipCargo = ship.getCargo();

		int tick = getContext().get(ContextCommon.class).getTick();

		StatVerkaeufe stats = em.createQuery("from StatVerkaeufe where tick=:tick and place=:place and system=:sys", StatVerkaeufe.class)
				.setParameter("tick", tick)
				.setParameter("place", kurse.getPlace())
				.setParameter("sys", tradepost.getSystem())
				.getSingleResult();

		Cargo statsCargo;
		if (stats == null)
		{
			stats = new StatVerkaeufe(tick, tradepost.getSystem(), kurse.getPlace());
			em.persist(stats);
		}

		statsCargo = stats.getStats();

		Cargo tpcargo = tradepost.getCargo();

		BigInteger totalRE = BigInteger.ZERO;
		boolean changed = false;

		StringBuilder message = new StringBuilder();

		Cargo kurseCargo = new Cargo(kurse.getKurse());
		kurseCargo.setOption(Cargo.Option.SHOWMASS, false);

		ResourceList reslist = kurseCargo.getResourceList();
		long freeSpace = tradepost.getTypeData().getCargo() - cargoService.getMass(tradepost.getCargo());
		long reconsumption = -1 * userService.getReBalance(tradepost.getOwner());
		BigInteger konto = tradepost.getOwner().getKonto();
		for (ResourceEntry res : reslist)
		{
			Long tmp = toMap.get(res.getId().toString());

			if (tmp != null && tmp > 0)
			{
				if (tmp > shipCargo.getResourceCount(res.getId()))
				{
					tmp = shipCargo.getResourceCount(res.getId());
				}

				long resourceMass = Cargo.getResourceMass(res.getId(), 1);

				//Wir wollen eventuell nur bis zu einem Limit ankaufen
				ResourceLimit resourceLimit = ResourceLimit.fuerSchiffUndItem(tradepost, res.getId());

				//Do we want to buy this resource from this player?
				if(resourceLimit == null || !resourceLimit.willBuy(tradepost.getOwner(), user)) {
					continue;
				}

				long limit = resourceLimit.getLimit();
				//Bereits gelagerte Bestaende abziehen
				limit -= tradepost.getCargo().getResourceCount(res.getId());

				if (tmp > limit)
				{
					long nichtVerkauft = tmp - limit;
					tmp = limit;

					message.append("[resource=").append(res.getId()).append("]").append(nichtVerkauft).append("[/resource] nicht verkauft - Es besteht kein Interesse mehr an dieser Ware\n");
					pmText.append("[resource=").append(res.getId()).append("]").append(nichtVerkauft).append("[/resource] konnten nicht gekauft werden, da die Ankaufgrenze überschritten werden würde\n");
				}

				//Nicht mehr ankaufen als Platz da ist
				if (tmp * resourceMass > freeSpace)
				{
					long nichtVerkauft = tmp - freeSpace / resourceMass;
					tmp = freeSpace / resourceMass;

					message.append("[resource=").append(res.getId()).append("]").append(nichtVerkauft).append("[/resource] nicht verkauft - alle Lager sind voll\n");
					pmText.append("[resource=").append(res.getId()).append("]").append(nichtVerkauft).append("[/resource] konnten nicht gekauft werden, da das Lager voll war\n");
				}

				BigDecimal get = BigDecimal.valueOf(tmp).multiply(BigDecimal.valueOf(res.getCount1() / 1000d));

				//Aufpassen das ich nicht das Konto leerfresse
				if (reconsumption > 0)
				{
					BigInteger ticks = konto.subtract(get.toBigInteger()).divide(BigInteger.valueOf(reconsumption));
					if (ticks.compareTo(BigInteger.valueOf(MIN_TICKS_TO_SURVIVE)) <= 0)
					{
						//Konto reicht mit Verkauf nur noch fuer weniger als 7 Ticks => begrenzen.
						long maximum = konto
								.subtract(BigInteger.valueOf(MIN_TICKS_TO_SURVIVE * reconsumption))
								.multiply(BigInteger.valueOf(1000))
								.divide(BigInteger.valueOf(res.getCount1())).longValue();

						message.append("[resource=").append(res.getId()).append("]").append(tmp - maximum).append("[/resource] nicht verkauft - Ihr Handelspartner ist pleite\n");
						pmText.append("[resource=").append(res.getId()).append("]").append(tmp - maximum).append("[/resource] konnten nicht gekauft werden, da Sie zu wenig Geld hatten\n");

						tmp = maximum;
					}
				}

				if (tmp <= 0)
				{
					continue;
				}

				get = BigDecimal.valueOf(tmp).multiply(BigDecimal.valueOf(res.getCount1() / 1000d));

				message.append("[resource=").append(res.getId()).append("]").append(tmp).append("[/resource] für ").append(Common.ln(get)).append(" RE verkauft\n");
				pmText.append("[resource=").append(res.getId()).append("]").append(tmp).append("[/resource] für ").append(Common.ln(get)).append(" RE\n");

				totalRE = totalRE.add(get.toBigInteger());
				changed = true;
				shipCargo.substractResource(res.getId(), tmp);

				statsCargo.addResource(res.getId(), tmp);
				tpcargo.addResource(res.getId(), tmp);
				//Freien Platz korrigieren
				freeSpace -= tmp * resourceMass;
				konto = konto.subtract(get.toBigInteger());
			}
		}

		if (changed)
		{
			stats.setStats(statsCargo);

			tradepost.setCargo(tpcargo);
			ship.setCargo(shipCargo);

			shipActionService.recalculateShipStatus(ship);
			//Benachrichtigung fuer HP-Besitzer schreiben
			var sendTradePostMessages = userValueService.getUserValue(user, WellKnownUserValue.GAMEPLAY_USER_HANDELSPOSTEN_PM);
			if(ship.getOwner().getId()!=tradepost.getOwner().getId() && Boolean.TRUE.equals(sendTradePostMessages))
			{
				pmService.send(tradepost.getOwner(), tradepost.getOwner().getId(), "Warenankauf an "+tradepost.getName(), pmText.toString());
			}


			user.transferMoneyFrom(tradepost.getOwner().getId(), totalRE,
					"Warenankauf an "+tradepost.getName()+" bei " + locationService.displayCoordinates(tradepost.getLocation(), false), false,
					UserMoneyTransfer.Transfer.SEMIAUTO);
		}

		return new RedirectViewResult("default").withMessage(message.toString());
	}

	private boolean isFull(Ship handelsposten)
	{
		return handelsposten.getTypeData().getCargo() <= cargoService.getMass(handelsposten.getCargo());
	}

	/**
	 * Zeigt die eigenen Waren sowie die Warenkurse am Handelsposten an.
	 *
	 * @param tradepost die ID des Handelspostens, an dem die Waren verkauft werden sollen
	 * @param ship die ID des Schiffes, das Waren verkaufen moechte
	 */
	@Action(ActionType.DEFAULT)
	public TemplateEngine defaultAction(Ship tradepost, Ship ship, RedirectViewResult redirect)
	{
		TemplateEngine t = templateViewResultFactory.createFor(this);

		validiereSchiff(ship);
		validiereHandelsposten(ship, tradepost);

		GtuWarenKurse kurse = ermittleWarenKurseFuerHandelsposten(ship, tradepost);

		if( redirect != null )
		{
			t.setVar("trade.message", Common._text(bbCodeParser, redirect.getMessage()));
		}

		t.setVar("global.shipid", ship.getId());
		t.setVar("global.tradepost", tradepost.getId());

		t.setVar("error.none", 1);
		t.setBlock("_TRADE", "res.listitem", "res.list");

		Cargo kurseCargo = new Cargo(kurse.getKurse());
		kurseCargo.setOption(Cargo.Option.SHOWMASS, false);
		ResourceList reslist = kurseCargo.getResourceList();

		// Block to check if user is an enemy
		User user = (User) getUser();

		if (!isFull(tradepost))
		{
			t.setVar("is.full", 0);
			for (ResourceEntry res : reslist)
			{
				if (!ship.getCargo().hasResource(res.getId()))
				{
					continue;
				}

				ResourceLimit limit = ResourceLimit.fuerSchiffUndItem(tradepost, res.getId());

				// Kaufen wir diese Ware vom Spieler?
				if (limit == null || !limit.willBuy(tradepost.getOwner(), user))
				{
					continue;
				}

				String preis;
				if (res.getCount1() < 50)
				{
					preis = "Kein Bedarf";
				}
				else
				{
					preis = Common.ln(res.getCount1() / 1000d) + " RE";
				}

				t.setVar("res.img", res.getImage(),
						"res.id", res.getId(),
						"res.name", res.getName(),
						"res.cargo", ship.getCargo().getResourceCount(res.getId()),
						"res.re", preis);

				t.parse("res.list", "res.listitem", true);
			}
		}
		else
		{

			t.setVar("is.full", true,
					"res.msg", "Dieser Handelsposten ist voll. Bitte beehren Sie uns zu einem späteren Zeitpunkt erneut.");
		}

		t.setBlock("_TRADE", "resbuy.listitem", "resbuy.list");

		ResourceList buyList = tradepost.getCargo().getResourceList();
		for (ResourceEntry resource : buyList)
		{
			SellLimit limit = SellLimit.fuerSchiffUndItem(tradepost, resource.getId());

			//Nicht kaeuflich
			if (limit == null || limit.getPrice() <= 0)
			{
				continue;
			}

			if (!limit.willSell(tradepost.getOwner(), user))
			{
				continue;
			}

			long buyable = tradepost.getCargo().getResourceCount(resource.getId()) - limit.getLimit();
			if (buyable <= 0)
			{
				continue;
			}


			t.setVar("resbuy.img", resource.getImage(),
					"resbuy.id", resource.getId(),
					"resbuy.name", resource.getName(),
					"resbuy.cargo", buyable,
					"resbuy.re", limit.getPrice());
			t.parse("resbuy.list", "resbuy.listitem", true);
		}

		return t;
	}
}
