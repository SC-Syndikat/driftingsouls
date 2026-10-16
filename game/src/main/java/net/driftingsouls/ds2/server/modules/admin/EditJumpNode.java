package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.entities.JumpNode_;
import net.driftingsouls.ds2.server.map.TileCache;
import net.driftingsouls.ds2.server.modules.admin.editoren.EditorForm8;
import net.driftingsouls.ds2.server.modules.admin.editoren.EntityEditor;

import javax.annotation.Nonnull;

@AdminMenuEntry(category = "Systeme", name="Sprungpunkt", permission = WellKnownAdminPermission.EDIT_JUMPNODE)
public class EditJumpNode implements EntityEditor<JumpNode>
{
	@Override
	public Class<JumpNode> getEntityType()
	{
		return JumpNode.class;
	}

	@Override
	public void configureFor(@Nonnull EditorForm8<JumpNode> form)
	{
		form.allowAdd();
		form.allowDelete();

		GameMasterRestriction restriction = GameMasterRestriction.forActiveUser();
		if (restriction.applies())
		{
			// A game master only connects star systems of the campaign
			form.restrictVisible(node -> restriction.isJumpNodeAllowed(node.getSystem(), node.getSystemOut()),
					(builder, root) -> restriction.getSystems().isEmpty()
							? builder.disjunction()
							: builder.and(root.get(JumpNode_.system).in(restriction.getSystems()),
									root.get(JumpNode_.systemOut).in(restriction.getSystems())));
			form.allowUpdate(node -> restriction.isJumpNodeAllowed(node.getSystem(), node.getSystemOut()));
			form.allowDelete(node -> restriction.isJumpNodeAllowed(node.getSystem(), node.getSystemOut()));
			form.validateUpdate((before, after) -> restriction.checkJumpNode(after.getSystem(), after.getSystemOut()));
		}
		form.field("Name", String.class, JumpNode::getName, JumpNode::setName);
		form.field("System", Integer.class, JumpNode::getSystem, JumpNode::setSystem);
		form.field("x", Integer.class, JumpNode::getX, JumpNode::setX);
		form.field("y", Integer.class, JumpNode::getY, JumpNode::setY);
		form.field("System (Ausgang)", Integer.class, JumpNode::getSystemOut, JumpNode::setSystemOut);
		form.field("x (Ausgang)", Integer.class, JumpNode::getXOut, JumpNode::setXOut);
		form.field("y (Ausgang)", Integer.class, JumpNode::getYOut, JumpNode::setYOut);
		form.field("GCP blockiert", Boolean.class, JumpNode::isGcpColonistBlock, JumpNode::setGcpColonistBlock);
		form.field("Versteckt", Boolean.class, JumpNode::isHidden, JumpNode::setHidden);
		form.field("Für bewaffnete Schiffe blockiert", Boolean.class, JumpNode::isWeaponBlock, JumpNode::setWeaponBlock);

		form.postUpdateTask("Sternenkarten-Cache leeren", (orgjumpnode,jumpnode) -> {
			TileCache.forSystem(orgjumpnode.getSystem()).resetCache();
			TileCache.forSystem(jumpnode.getSystem()).resetCache();
		});
		form.preDeleteTask("Sternenkarten-Cache leeren", (jumpnode) -> TileCache.forSystem(jumpnode.getSystem()).resetCache());
	}
}
