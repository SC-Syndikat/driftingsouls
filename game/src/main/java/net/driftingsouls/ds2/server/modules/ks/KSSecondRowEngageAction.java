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
package net.driftingsouls.ds2.server.modules.ks;

import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.battles.BattleFlag;
import net.driftingsouls.ds2.server.battles.BattleShip;
import net.driftingsouls.ds2.server.battles.BattleShipFlag;
import net.driftingsouls.ds2.server.battles.SchlachtLogAktion;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;

import java.io.IOException;
import java.util.List;

/**
 * Ermoeglicht das Vorruecken gegen eine instabile zweite Reihe des Gegners.
 * @author Christopher Jung
 *
 */
public class KSSecondRowEngageAction extends BasicKSAction {
	/**
	 * Konstruktor.
	 *
	 */
	public KSSecondRowEngageAction() {
	}
	
	@Override
	public Result validate(Battle battle) {
		Context context = ContextMap.getContext();
		int enemySide = context.getRequest().getParameterInt("enemySide");
		User enemy = (User)context.getDB().get(User.class, battle.getUser(enemySide).getId());
		if( battle.isSecondRowStable(battle.getSide(enemy)) ) {
			return Result.ERROR;
		}  
		
		return Result.OK;
	}

	@Override
	public Result execute(TemplateEngine t, Battle battle) throws IOException {
		Context context = ContextMap.getContext();
		User user = (User)context.getActiveUser();
		int enemySide = context.getRequest().getParameterInt("enemySide");
		User enemy = (User)context.getDB().get(User.class, battle.getUser(enemySide).getId());
		Result result = super.execute(t, battle);
		if( result != Result.OK ) {
			return result;
		}
		
		if( this.validate(battle) != Result.OK ) {
			battle.logme("Die Aktion kann nicht ausgef&uuml;hrt werden");
			return Result.ERROR;
		}

		List<BattleShip> enemyShips = battle.getShips(enemy);
		for (BattleShip eShip : enemyShips)
		{
			eShip.removeFlag(BattleShipFlag.SECONDROW);
		}
		
		battle.logme( "Ihre Schiffe r&uuml;cken vor und durchbrechen die feindlichen Linien\n");
		battle.log(new SchlachtLogAktion(battle.getSide(user), "Die feindlichen Schiffe r&uuml;cken vor und durchbrechen trotz heftigen Widerstands die Linien"));

		battle.setSideFlag(battle.getSide(user),BattleFlag.BLOCK_SECONDROW, true);
		
		return Result.OK;
	}
}
