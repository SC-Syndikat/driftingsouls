package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.config.ConfigFelsbrockenSystem;
import net.driftingsouls.ds2.server.config.ConfigFelsbrockenSystem_;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.config.StarSystem_;
import net.driftingsouls.ds2.server.modules.admin.editoren.EditorForm8;
import net.driftingsouls.ds2.server.modules.admin.editoren.EntityEditor;

import javax.annotation.Nonnull;

@AdminMenuEntry(category = "Systeme", name="Felsbrocken-System", permission = WellKnownAdminPermission.EDIT_CONFIG_FELSBROCKEN_SYSTEM)
public class EditConfigFelsbrockenSystem implements EntityEditor<ConfigFelsbrockenSystem>
{
	@Override
	public Class<ConfigFelsbrockenSystem> getEntityType()
	{
		return ConfigFelsbrockenSystem.class;
	}

	@Override
	public void configureFor(@Nonnull EditorForm8<ConfigFelsbrockenSystem> form)
	{
		form.allowAdd();
		form.allowDelete();

		GameMasterRestriction restriction = GameMasterRestriction.forActiveUser();
		if (restriction.applies())
		{
			// A game master only configures asteroids of campaign star systems
			form.restrictVisible(config -> restriction.isSystemAllowed(config.getSystem().getID()),
					(builder, root) -> restriction.getSystems().isEmpty()
							? builder.disjunction()
							: root.get(ConfigFelsbrockenSystem_.system).get(StarSystem_.id).in(restriction.getSystems()));
			form.allowUpdate(config -> restriction.isSystemAllowed(config.getSystem().getID()));
			form.allowDelete(config -> restriction.isSystemAllowed(config.getSystem().getID()));
			form.validateUpdate((before, after) -> restriction.checkSystem(after.getSystem().getID()));
		}
		form.ifUpdating().label("Sternensystem", ConfigFelsbrockenSystem::getSystem);
		var system = form.ifAdding().field("Sternensystem", StarSystem.class, ConfigFelsbrockenSystem::getSystem, ConfigFelsbrockenSystem::setSystem);
		if (restriction.applies())
		{
			system.withOptions(GameMasterOptions.systems(restriction));
		}
		form.field("Name der Felsbrocken", String.class, ConfigFelsbrockenSystem::getName, ConfigFelsbrockenSystem::setName);
        form.field("Anzahl Felsbrocken", Integer.class, ConfigFelsbrockenSystem::getCount, ConfigFelsbrockenSystem::setCount);
		form.label("Anzahl Layouts", (t) -> t.getFelsbrocken().size());
	}
}
