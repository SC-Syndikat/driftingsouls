package net.driftingsouls.ds2.server.campaign.dialog;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParamDefTest
{
	@Test
	public void gegebenEinVerweis_validate_sollteEineIdVerlangen()
	{
		ParamDef template = ParamDef.reference(ParamDef.Type.SHIP_GROUP, "template", "Vorlage");

		assertNull(template.validate("3"));
		assertNotNull(template.validate("abc"));
		assertNotNull("Pflichtfeld", template.validate(""));
	}

	@Test
	public void gegebenEinOptionalerVerweis_validate_sollteLeerErlauben()
	{
		ParamDef owner = ParamDef.optionalReference(ParamDef.Type.CAMPAIGN_NPC, "owner", "Besitzer", "der sprechende NPC");

		assertNull(owner.validate(""));
		assertNull(owner.validate("-5"));
		assertEquals("der sprechende NPC", owner.emptyLabel);
	}

	@Test
	public void gegebenSchiffstypen_validate_sollteEineIdListeVerlangen()
	{
		ParamDef types = ParamDef.list(ParamDef.Type.SHIP_TYPES, "types", "Schiffstypen", false);

		assertNull(types.validate("1,2, 3"));
		assertNull(types.validate("7"));
		assertNotNull(types.validate("1,a"));
		assertNotNull("Ohne Unverändert/Alle kein Strich", types.validate("-"));
	}

	@Test
	public void gegebenSchiffsklassen_validate_sollteBekannteKlassenVerlangen()
	{
		ParamDef classes = ParamDef.list(ParamDef.Type.SHIP_CLASSES, "classes", "Schiffsklassen", true);

		assertNull(classes.validate("JAEGER,KORVETTE"));
		assertNull("Strich = alle", classes.validate("-"));
		assertNull("leer = unverändert", classes.validate(""));
		assertNotNull(classes.validate("JAEGER,RAUMSCHLACHTSCHIFF"));
		assertTrue(classes.keepOrAll);
	}

	@Test
	public void gegebenEinOrt_validate_sollteSystemUndKoordinatenVerlangen()
	{
		ParamDef location = ParamDef.location("location", "Ort", false);

		assertNull(location.validate("3:10/20"));
		assertNull(location.validate(""));
		assertNotNull(location.validate("3:10"));
		assertNotNull(location.validate("x:1/2"));
	}
}
