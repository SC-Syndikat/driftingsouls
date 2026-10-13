package net.driftingsouls.ds2.server.campaign.dialog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DialogTextTest
{
	@Test
	public void gegebenBedingteBloecke_resolve_sollteDenPassendenTeilZeigen()
	{
		// Outside a campaign every variable is 0
		String text = "Notruf: [wenn teile == 0]##rausch##[/wenn][wenn teile >= 1]Kurier Daro[sonst] ?[/wenn] Ende";

		assertEquals("Notruf: ##rausch## ? Ende", DialogText.resolve(text, null));
	}

	@Test
	public void gegebenTextOhneBloecke_resolve_sollteIhnUnveraendertLassen()
	{
		assertEquals("[b]Halt![/b]", DialogText.resolve("[b]Halt![/b]", null));
	}
}
