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
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import net.driftingsouls.ds2.server.ships.Alarmstufe;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipTypeData;

import java.io.IOException;

/**
 * Aktiviert bei einem Schiff in der Schlacht den roten Alarm.
 *
 */
public class KSActivateAR extends BasicKSMenuAction
{
    @Override
    public Result validate(Battle battle) {

        ShipTypeData shiptype = battle.getOwnShip().getShip().getTypeData();

        if(battle.getOwnShip().getShip().getAlarm() != Alarmstufe.RED && (shiptype.getShipClass() != ShipClasses.GESCHUETZ) && shiptype.isMilitary())
        {
            return Result.OK;
        }
        return Result.ERROR;
    }

    @Override
    public Result execute(TemplateEngine t, Battle battle) throws IOException
    {
        Result result = super.execute(t, battle);
        if( result != Result.OK ) {
            return result;
        }

        battle.getOwnShip().getShip().setAlarm(Alarmstufe.RED);

        return Result.OK;
    }
}
