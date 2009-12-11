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
package net.driftingsouls.ds2.server.bases;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ItemID;
import net.driftingsouls.ds2.server.cargo.ResourceEntry;
import net.driftingsouls.ds2.server.cargo.ResourceList;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * Die BuddelStaette.
 *
 */
@Entity(name="DigBuilding")
@DiscriminatorValue("net.driftingsouls.ds2.server.bases.DigBuilding")
@Configurable
public class DigBuilding extends DefaultBuilding {
	
	/**
	 * Erstellt eine neue Buddelstaetten-Instanz.
	 */
	public DigBuilding() {
		// EMPTY
	}
	
	@Override
	public boolean classicDesign() {
		return true;
	}
	
	@SuppressWarnings("unchecked")
	private Cargo getProzentProduces()
	{
		Cargo production = new Cargo();
		Map<Integer, Double> productions = new HashMap<Integer, Double>();
		
		String digbuilding = getChanceRess();
		
		if(digbuilding == null || digbuilding.equals(""))
		{
			return production;
		}
		
		String[] digs = StringUtils.split(digbuilding, "|");
		for(String dig : digs)
		{
			String[] thisdig = StringUtils.split(dig, ";");
			productions.put(Integer.valueOf(thisdig[0]), Double.valueOf(thisdig[1]));
		}
		
		if(productions.isEmpty())
		{
			return production;
		}
		
		for( Iterator<?> iter = productions.entrySet().iterator(); iter.hasNext(); )
		{
			Entry<Integer, Double> entry = (Entry<Integer, Double>) iter.next();
			production.addResource(new ItemID(entry.getKey()), entry.getValue().longValue());
		}
		
		return production;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Cargo getAllProduces()
	{
		Cargo production = new Cargo();
		Map<Integer, Double> productions = new HashMap<Integer, Double>();
		
		String digbuilding = getChanceRess();
		
		if(digbuilding == null || digbuilding.equals(""))
		{
			return production;
		}
		
		String[] digs = StringUtils.split(digbuilding, "|");
		for(String dig : digs)
		{
			String[] thisdig = StringUtils.split(dig, ";");
			productions.put(Integer.valueOf(thisdig[0]), 1d);
		}
		
		if(productions.isEmpty())
		{
			return production;
		}
		
		for( Iterator<?> iter = productions.entrySet().iterator(); iter.hasNext(); )
		{
			Entry<Integer, Double> entry = (Entry<Integer, Double>) iter.next();
			production.addResource(new ItemID(entry.getKey()), entry.getValue().longValue());
		}
		
		return production;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Cargo getProduces()
	{
		Cargo production = new Cargo();
		Map<Integer, Double> productions = new HashMap<Integer, Double>();
		
		String digbuilding = getChanceRess();
		
		if(digbuilding == null || digbuilding.equals(""))
		{
			return production;
		}
		
		String[] digs = StringUtils.split(digbuilding, "|");
		for(String dig : digs)
		{
			String[] thisdig = StringUtils.split(dig, ";");
			productions.put(Integer.valueOf(thisdig[0]), Double.valueOf(thisdig[1]));
		}
		
		if(productions.isEmpty())
		{
			return production;
		}
		
		for( Iterator<?> iter = productions.entrySet().iterator(); iter.hasNext(); )
		{
			Entry<Integer, Double> entry = (Entry<Integer, Double>) iter.next();
			double rnd = Math.random();
			if( rnd <= entry.getValue()/100)
			{
				production.addResource(new ItemID(entry.getKey()), 1l);
			}
		}
		
		return production;
	}
	
	@Override
	public String output(Context context, TemplateEngine t, Base base, int field, int building) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("Dieses Geb&auml;de ist eine Buddelst&auml;tte.<br />\n");
		buffer.append("Die bei der Produktion angegebenen Werte sind die Chancen die entsprechende Ressource zu finden.<br />\n");
		buffer.append("<br />\n");
		buffer.append("<br />\n");
		buffer.append("Verbraucht:<br />\n");
		buffer.append("<div align=\"center\">\n");
		
		boolean entry = false;
		ResourceList reslist = getConsumes().getResourceList();
		for( ResourceEntry res : reslist )
		{
			buffer.append("<img src=\""+res.getImage()+"\" alt=\"\" />"+res.getCargo1()+" ");
			entry = true;
		}
	
		if( getEVerbrauch() > 0 )
		{
			buffer.append("<img src=\""+this.config.get("URL")+"data/interface/energie.gif\" alt=\"\" />"+getEVerbrauch()+" ");
			entry = true;
		}
		if( !entry )
		{
			buffer.append("-");
		}
		
		buffer.append("</div>\n");
		
		buffer.append("Produziert:<br />\n");
		buffer.append("<div align=\"center\">\n");
		
		entry = false;
		reslist = getProzentProduces().getResourceList();
		for( ResourceEntry res : reslist )
		{
			buffer.append("<img src=\""+res.getImage()+"\" alt=\"\" />"+res.getCount1()+"% ");
			entry = true;
		}
		
		if( getEProduktion() > 0 )
		{
			buffer.append("<img src=\""+this.config.get("URL")+"data/interface/energie.gif\" alt=\"\" />"+getEProduktion());
			entry = true;
		}
	
		if( !entry ) 
		{ 
			buffer.append("-");
		}
		buffer.append("</div><br />\n");
		return buffer.toString();
	}
}