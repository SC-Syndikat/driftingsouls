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
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import net.driftingsouls.ds2.server.ships.Alarmstufe;

import java.io.IOException;

/**
 * Deaktiviert bei einem Schiff in der Schlacht den roten Alarm.
 *
 */
public class KSDeactivateAR extends BasicKSMenuAction
{
    @Override
    public Result validate(Battle battle) {
		User user = (User)ContextMap.getContext().getActiveUser();
        if(battle.getShip(user).getShip().getAlarm() == Alarmstufe.RED)
        {
            return Result.OK;
        }
        return Result.ERROR;
    }

    @Override
    public Result execute(TemplateEngine t, Battle battle) throws IOException
    {
        User user = (User)ContextMap.getContext().getActiveUser();
        Result result = super.execute(t, battle);
        if( result != Result.OK ) {
            return result;
        }

        battle.getShip(user).getShip().setAlarm(Alarmstufe.GREEN);

        return Result.OK;
    }
}
