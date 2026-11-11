package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.entities.jooq.tables.records.ShipGroupTemplateEntryRecord;
import net.driftingsouls.ds2.server.entities.jooq.tables.records.ShipGroupTemplateRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.SHIP_GROUP_TEMPLATE;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.SHIP_GROUP_TEMPLATE_ENTRY;

/**
 * Persistence for ship group templates. Both tables are jOOQ-owned.
 */
public class ShipGroupRepository
{
	public List<ShipGroupTemplate> findAll()
	{
		return withDb(db -> {
			List<ShipGroupTemplate> templates = new ArrayList<>();
			for (ShipGroupTemplateRecord record : db.selectFrom(SHIP_GROUP_TEMPLATE).orderBy(SHIP_GROUP_TEMPLATE.NAME).fetch())
			{
				templates.add(toTemplate(record, findEntries(record.getId())));
			}
			return templates;
		});
	}

	public Optional<ShipGroupTemplate> find(int id)
	{
		return withDb(db -> db.selectFrom(SHIP_GROUP_TEMPLATE)
				.where(SHIP_GROUP_TEMPLATE.ID.eq(id))
				.fetchOptional()
				.map(record -> toTemplate(record, findEntries(id))));
	}

	public int create(String name, String description, boolean createFleet)
	{
		return withDb(db -> {
			ShipGroupTemplateRecord record = db.newRecord(SHIP_GROUP_TEMPLATE);
			record.setName(name);
			record.setDescription(description);
			record.setCreateFleet((byte) (createFleet ? 1 : 0));
			record.store();
			return record.getId();
		});
	}

	public void update(int id, String name, String description, boolean createFleet)
	{
		withDb(db -> db.update(SHIP_GROUP_TEMPLATE)
				.set(SHIP_GROUP_TEMPLATE.NAME, name)
				.set(SHIP_GROUP_TEMPLATE.DESCRIPTION, description)
				.set(SHIP_GROUP_TEMPLATE.CREATE_FLEET, (byte) (createFleet ? 1 : 0))
				.where(SHIP_GROUP_TEMPLATE.ID.eq(id))
				.execute());
	}

	public void delete(int id)
	{
		withDb(db -> db.deleteFrom(SHIP_GROUP_TEMPLATE).where(SHIP_GROUP_TEMPLATE.ID.eq(id)).execute());
	}

	public void addEntry(int templateId, ShipGroupTemplate.Entry entry)
	{
		withDb(db -> {
			ShipGroupTemplateEntryRecord record = db.newRecord(SHIP_GROUP_TEMPLATE_ENTRY);
			record.setTemplateId(templateId);
			apply(record, entry);
			return record.store();
		});
	}

	/**
	 * Replaces an entry's ships, cargo, units, officer and alarm.
	 */
	public void updateEntry(int templateId, ShipGroupTemplate.Entry entry)
	{
		withDb(db -> {
			ShipGroupTemplateEntryRecord record = db.selectFrom(SHIP_GROUP_TEMPLATE_ENTRY)
					.where(SHIP_GROUP_TEMPLATE_ENTRY.ID.eq(entry.id))
					.and(SHIP_GROUP_TEMPLATE_ENTRY.TEMPLATE_ID.eq(templateId))
					.fetchOptional()
					.orElseThrow(() -> new CampaignException("Unbekannter Eintrag: " + entry.id));
			apply(record, entry);
			return record.store();
		});
	}

	private static void apply(ShipGroupTemplateEntryRecord record, ShipGroupTemplate.Entry entry)
	{
		record.setShipTypeId(entry.shipTypeId);
		record.setCount(entry.count);
		record.setNamePattern(entry.namePattern);
		record.setCargo(entry.cargo);
		record.setUnits(entry.units);
		record.setModules(entry.modules);
		ShipGroupTemplate.Officer officer = entry.officer;
		record.setOfficerNav(officer == null ? null : officer.nav);
		record.setOfficerIng(officer == null ? null : officer.ing);
		record.setOfficerWaf(officer == null ? null : officer.waf);
		record.setOfficerSec(officer == null ? null : officer.sec);
		record.setOfficerCom(officer == null ? null : officer.com);
		record.setOfficerSpecial(officer == null ? "" : officer.special);
		record.setAlarm(entry.alarm);
	}

	public void deleteEntry(int templateId, int entryId)
	{
		withDb(db -> db.deleteFrom(SHIP_GROUP_TEMPLATE_ENTRY)
				.where(SHIP_GROUP_TEMPLATE_ENTRY.ID.eq(entryId))
				.and(SHIP_GROUP_TEMPLATE_ENTRY.TEMPLATE_ID.eq(templateId))
				.execute());
	}

	private List<ShipGroupTemplate.Entry> findEntries(int templateId)
	{
		return withDb(db -> {
			List<ShipGroupTemplate.Entry> entries = new ArrayList<>();
			for (ShipGroupTemplateEntryRecord r : db.selectFrom(SHIP_GROUP_TEMPLATE_ENTRY)
					.where(SHIP_GROUP_TEMPLATE_ENTRY.TEMPLATE_ID.eq(templateId))
					.orderBy(SHIP_GROUP_TEMPLATE_ENTRY.ID)
					.fetch())
			{
				entries.add(new ShipGroupTemplate.Entry(r.getId(), r.getShipTypeId(), r.getCount(), r.getNamePattern(),
						r.getCargo(), r.getUnits(), r.getModules(), toOfficer(r), r.getAlarm()));
			}
			return entries;
		});
	}

	private static ShipGroupTemplate.Officer toOfficer(ShipGroupTemplateEntryRecord r)
	{
		if (r.getOfficerNav() == null)
		{
			return null;
		}
		return new ShipGroupTemplate.Officer(r.getOfficerNav(), nullToZero(r.getOfficerIng()), nullToZero(r.getOfficerWaf()),
				nullToZero(r.getOfficerSec()), nullToZero(r.getOfficerCom()), r.getOfficerSpecial());
	}

	private static int nullToZero(Integer value)
	{
		return value == null ? 0 : value;
	}

	private static ShipGroupTemplate toTemplate(ShipGroupTemplateRecord record, List<ShipGroupTemplate.Entry> entries)
	{
		return new ShipGroupTemplate(record.getId(), record.getName(), record.getDescription(), record.getCreateFleet() != 0, entries);
	}
}
