package net.driftingsouls.ds2.server.campaign.dialog;

import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.CampaignText;
import net.driftingsouls.ds2.server.campaign.ShipGroupRepository;
import net.driftingsouls.ds2.server.campaign.ShipGroupTemplate;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.config.Medals;
import net.driftingsouls.ds2.server.config.Rang;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.Forschung;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.ally.Ally;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import net.driftingsouls.ds2.server.units.UnitType;

import javax.persistence.EntityManager;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * What the parameters of dialog factors and actions refer to: the choices the editor offers, and the
 * names the descriptions show instead of bare ids. Each list is loaded once per instance.
 */
public class ParamChoices
{
	private final EntityManager em;
	private final Map<ParamDef.Type, Map<String, String>> cache = new EnumMap<>(ParamDef.Type.class);
	private Map<String, String> campaignSystems;

	public ParamChoices(EntityManager em)
	{
		this.em = em;
	}

	/**
	 * @return The instance shared by the current request
	 */
	public static ParamChoices forCurrentRequest()
	{
		Context context = ContextMap.getContext();
		ParamChoices choices = (ParamChoices) context.getVariable(ParamChoices.class, "instance");
		if (choices == null)
		{
			choices = new ParamChoices(context.getEM());
			context.putVariable(ParamChoices.class, "instance", choices);
		}
		return choices;
	}

	/**
	 * The name of a stored value for a description. Falls back to the value itself when there is no
	 * request to look it up in, or nothing by that id.
	 */
	public static String describe(ParamDef.Type type, String value)
	{
		try
		{
			return forCurrentRequest().label(type, value);
		}
		catch (RuntimeException e)
		{
			return value;
		}
	}

	/**
	 * @param type One of {@link ParamDef#REFERENCES} or {@link ParamDef#LISTS}
	 * @return Stored value to label
	 */
	public Map<String, String> get(ParamDef.Type type)
	{
		return cache.computeIfAbsent(type, this::load);
	}

	/**
	 * @return The star systems of the open campaign, for {@link ParamDef.Type#LOCATION}
	 */
	public Map<String, String> getCampaignSystems()
	{
		if (campaignSystems == null)
		{
			campaignSystems = new LinkedHashMap<>();
			Set<Integer> ids = new CampaignMapService(em).getOpenCampaignSystemIds();
			if (!ids.isEmpty())
			{
				for (StarSystem system : em.createQuery("from StarSystem where id in (:ids) order by id", StarSystem.class)
						.setParameter("ids", ids)
						.getResultList())
				{
					campaignSystems.put(Integer.toString(system.getID()), CampaignText.plain(system.getName()) + " (" + system.getID() + ")");
				}
			}
		}
		return campaignSystems;
	}

	/**
	 * @return The label of a stored value; for lists the labels of all entries. Unknown entries keep
	 * their value.
	 */
	public String label(ParamDef.Type type, String value)
	{
		if (value == null || value.isBlank())
		{
			return "";
		}
		if (ParamDef.LISTS.contains(type))
		{
			if (ParamDef.ALL.equals(value.trim()))
			{
				return "alle";
			}
			StringJoiner labels = new StringJoiner(", ");
			for (String entry : value.split(","))
			{
				String trimmed = entry.trim();
				labels.add(get(type).getOrDefault(type == ParamDef.Type.SHIP_CLASSES ? trimmed.toUpperCase() : trimmed, trimmed));
			}
			return labels.toString();
		}
		return get(type).getOrDefault(value.trim(), value.trim());
	}

	private Map<String, String> load(ParamDef.Type type)
	{
		Map<String, String> choices = new LinkedHashMap<>();
		switch (type)
		{
			case SHIP_GROUP:
				for (ShipGroupTemplate template : new ShipGroupRepository().findAll())
				{
					choices.put(Integer.toString(template.id), template.name + " (" + template.id + ")");
				}
				break;
			case CAMPAIGN_NPC:
			{
				Set<Integer> ids = new TreeSet<>();
				CampaignService campaigns = new CampaignService(em);
				campaigns.findOpenCampaign().ifPresent(c -> campaigns.getParticipantIds(c).stream().filter(id -> id < 0).forEach(ids::add));
				for (int id : ids)
				{
					User npc = em.find(User.class, id);
					choices.put(Integer.toString(id), (npc == null ? "?" : CampaignText.plain(npc.getName())) + " (" + id + ")");
				}
				break;
			}
			case INTERACTION:
				for (Interaction interaction : new InteractionRepository().findAll())
				{
					choices.put(Integer.toString(interaction.id), interaction.label + " (" + interaction.id + ")");
				}
				break;
			case CAMPAIGN_JUMP_NODE:
				for (int id : new TreeSet<>(new CampaignMapService(em).getJumpNodeStates().keySet()))
				{
					JumpNode node = em.find(JumpNode.class, id);
					choices.put(Integer.toString(id), node == null ? "? (" + id + ")"
							: CampaignText.plain(node.getName()) + " (" + id + ") – " + node.getSystem() + ":" + node.getX() + "/" + node.getY()
							+ " → " + node.getSystemOut() + ":" + node.getXOut() + "/" + node.getYOut());
				}
				break;
			case RESEARCH:
				for (Forschung research : em.createQuery("from Forschung order by name", Forschung.class).getResultList())
				{
					choices.put(Integer.toString(research.getID()), CampaignText.plain(research.getName()) + " (" + research.getID() + ")");
				}
				break;
			case UNIT_TYPE:
				for (UnitType unit : em.createQuery("from UnitType order by name", UnitType.class).getResultList())
				{
					choices.put(Integer.toString(unit.getId()), CampaignText.plain(unit.getName()) + " (" + unit.getId() + ")");
				}
				break;
			case ALLIANCE:
				for (Ally ally : em.createQuery("from Ally order by id", Ally.class).getResultList())
				{
					choices.put(Integer.toString(ally.getId()), CampaignText.plain(ally.getName()) + " (" + ally.getId() + ")");
				}
				break;
			case RANK:
				choices.put("0", "kein Rang (0)");
				for (Rang rank : Medals.get().ranks().values())
				{
					choices.put(Integer.toString(rank.getId()), CampaignText.plain(rank.getName()) + " (" + rank.getId() + ")");
				}
				break;
			case SHIP_TYPES:
				for (ShipType shipType : em.createQuery("from ShipType order by id", ShipType.class).getResultList())
				{
					choices.put(Integer.toString(shipType.getId()), CampaignText.plain(shipType.getNickname()) + " (" + shipType.getId() + ")");
				}
				break;
			case SHIP_CLASSES:
				for (ShipClasses shipClass : ShipClasses.values())
				{
					choices.put(shipClass.name(), shipClass.getSingular());
				}
				break;
			default:
				throw new IllegalArgumentException("Keine Auswahl für Parametertyp " + type);
		}
		return choices;
	}
}
