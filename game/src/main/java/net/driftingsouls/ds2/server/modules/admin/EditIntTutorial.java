package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.entities.IntTutorial;
import net.driftingsouls.ds2.server.modules.admin.editoren.EditorForm8;
import net.driftingsouls.ds2.server.modules.admin.editoren.EntityEditor;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@AdminMenuEntry(category = "Sonstiges", name="Tutorial", permission = WellKnownAdminPermission.EDIT_INT_TUTORIAL)
@Component
public class EditIntTutorial implements EntityEditor<IntTutorial>
{
	@Override
	public Class<IntTutorial> getEntityType()
	{
		return IntTutorial.class;
	}

	@Override
	public void configureFor(@NonNull EditorForm8<IntTutorial> form)
	{
		form.allowAdd();
		form.allowDelete();

		form.dynamicContentField("Kopfgrafik", IntTutorial::getHeadImg, IntTutorial::setHeadImg);
		form.field("Basis benötigt", Boolean.class, IntTutorial::isReqBase, IntTutorial::setReqBase);
		form.field("Schiff benötigt", Boolean.class, IntTutorial::isReqShip, IntTutorial::setReqShip);
		form.field("Name wurde geändert", Boolean.class, IntTutorial::isReqName, IntTutorial::setReqName);
		form.field("Vorherige Tutorialseite", IntTutorial.class, IntTutorial::getBenoetigteSeite, IntTutorial::setBenoetigteSeite).withNullOption("[Keine]");
		form.textArea("Text", IntTutorial::getText, IntTutorial::setText);

		form.preDeleteTask("Abhängige Tutorialseiten aktualisieren", (it) -> it.getAbhaengigeSeiten().forEach((it2) -> it2.setBenoetigteSeite(null)));
	}
}
