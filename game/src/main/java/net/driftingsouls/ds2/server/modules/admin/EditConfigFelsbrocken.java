package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.ConfigFelsbrocken;
import net.driftingsouls.ds2.server.config.ConfigFelsbrockenSystem;
import net.driftingsouls.ds2.server.config.ConfigFelsbrockenSystem_;
import net.driftingsouls.ds2.server.config.ConfigFelsbrocken_;
import net.driftingsouls.ds2.server.config.StarSystem_;
import net.driftingsouls.ds2.server.modules.admin.editoren.EditorForm8;
import net.driftingsouls.ds2.server.modules.admin.editoren.EntityEditor;
import net.driftingsouls.ds2.server.ships.ShipType;

import javax.annotation.Nonnull;

@AdminMenuEntry(category = "Systeme", name="Felsbrocken-Layout", permission = WellKnownAdminPermission.EDIT_CONFIG_FELSBROCKEN)
public class EditConfigFelsbrocken implements EntityEditor<ConfigFelsbrocken>
{
	@Override
	public Class<ConfigFelsbrocken> getEntityType()
	{
		return ConfigFelsbrocken.class;
	}

	@Override
	public void configureFor(@Nonnull EditorForm8<ConfigFelsbrocken> form)
	{
		form.allowAdd();
		form.allowDelete();

		GameMasterRestriction restriction = GameMasterRestriction.forActiveUser();
		if (restriction.applies())
		{
			// A game master only configures asteroids of campaign star systems
			form.restrictVisible(layout -> restriction.isSystemAllowed(layout.getSystem().getSystem().getID()),
					(builder, root) -> restriction.getSystems().isEmpty()
							? builder.disjunction()
							: root.get(ConfigFelsbrocken_.system).get(ConfigFelsbrockenSystem_.system).get(StarSystem_.id).in(restriction.getSystems()));
			form.allowUpdate(layout -> restriction.isSystemAllowed(layout.getSystem().getSystem().getID()));
			form.allowDelete(layout -> restriction.isSystemAllowed(layout.getSystem().getSystem().getID()));
			form.validateUpdate((before, after) -> restriction.checkSystem(after.getSystem().getSystem().getID()));
		}
		var system = form.field("System mit Felsbrocken", ConfigFelsbrockenSystem.class, ConfigFelsbrocken::getSystem, ConfigFelsbrocken::setSystem);
		if (restriction.applies())
		{
			system.withOptions(GameMasterOptions.felsbrockenSystems(restriction));
		}
		form.field("Schiffstyp", ShipType.class, ConfigFelsbrocken::getShiptype, ConfigFelsbrocken::setShiptype);
		form.field("Wahrscheinlichkeitsfaktor", Integer.class, ConfigFelsbrocken::getChance, ConfigFelsbrocken::setChance);
		form.field("Cargo", Cargo.class, ConfigFelsbrocken::getCargo, ConfigFelsbrocken::setCargo);
	}
}
