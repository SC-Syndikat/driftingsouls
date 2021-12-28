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
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipTypeData;

import java.io.IOException;

/**
 * Ermoeglicht den Angriff auf die zweite Reihe des Gegners.
 * @author Christopher Jung
 *
 */
public class KSSecondRowAttackAction extends BasicKSAction {
	/**
	 * Konstruktor.
	 *
	 */
	public KSSecondRowAttackAction() {
	}

	@Override
	public Result validate(Battle battle) {
		if( battle.hasFlag(BattleFlag.FIRSTROUND) ) {
			return Result.ERROR;
		}
		User user = (User)ContextMap.getContext().getActiveUser();
		Context context = ContextMap.getContext();
		int enemySide = context.getRequest().getParameterInt("enemySide");
		User enemy = (User)context.getDB().get(User.class, battle.getUser(enemySide).getId());
		int seite = battle.getSide(user);
		if( battle.hasSideFlag(seite,BattleFlag.DROP_SECONDROW) ) {
			return Result.ERROR;
		}

		//The attacker needs a destroyer
		boolean hasDestroyer = false;
		for(BattleShip aship: battle.getShips(seite))
		{
			if( aship.hasFlag(BattleShipFlag.FLUCHT) || aship.hasFlag(BattleShipFlag.JOIN) ||
				aship.hasFlag(BattleShipFlag.SECONDROW) )
			{
				continue;
			}
			ShipTypeData shiptype = aship.getTypeData();

			if( shiptype.getShipClass() == ShipClasses.ZERSTOERER ) {
				hasDestroyer = true;
			}
		}

		if( !hasDestroyer ) {
			return Result.ERROR;
		}

		if(battle.getBattleValue(seite) < battle.getBattleValue(battle.getSide(enemy))*2)
		{
			return Result.ERROR;
		}

		return Result.OK;
	}

	@Override
	public Result execute(TemplateEngine t, Battle battle) throws IOException {
		Result result = super.execute(t, battle);
		if( result != Result.OK ) {
			return result;
		}
		User user = (User)ContextMap.getContext().getActiveUser();
		Context context = ContextMap.getContext();
		int enemySide = context.getRequest().getParameterInt("enemySide");
		User enemy = (User)context.getDB().get(User.class, battle.getUser(enemySide).getId());
		if( this.validate(battle) != Result.OK ) {
			battle.logme("Die Aktion kann nicht ausgef&uuml;hrt werden");
			return Result.ERROR;
		}

		battle.setSideFlag(battle.getSide(enemy),BattleFlag.DROP_SECONDROW, true);

		battle.logme( "Ihre Schiffe r&uuml;cken vor und dr&auml;ngen die feindlichen Linien unter schwerem Feuer langsam zur&uuml;ck");
		battle.log(new SchlachtLogAktion(battle.getSide(user), "Die feindlichen Schiffe rücken unter schwerem Feuer langsam vor und drängen trotz heftigsten Widerstands die Linien zurück\n"));

		return Result.OK;
	}
}
