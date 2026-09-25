package net.driftingsouls.ds2.server.modules.thymeleaf;

import net.driftingsouls.ds2.server.AdminCommands;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignObject;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.CampaignUserView;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.modules.admin.CampaignParticipants;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.repositories.StarsystemRepository;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.KeineTicksperre;
import net.driftingsouls.ds2.server.WellKnownConfigValue;

import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.WebContext;

import javax.persistence.EntityManager;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@KeineTicksperre
public class GameMasterController implements DSController {
    private Context context;

    private enum Action{
      TICK,
      START,
      END,
      RELOCATE,
      DEFAULT
    }

    private enum Show{
      START,
      TICK,
      END,
      DEFAULT
    }

    /**
     * Erzeugt die GameMastersseite (/gamemaster).
     * URL-Parameter:
     * action: <code>null</code> oder "tick"
     */
    @Override
    public void process(HttpServletRequest request, HttpServletResponse response, ServletContext servletContext, ITemplateEngine templateEngine) throws Exception {
        context = ContextMap.getContext();
        WebContext ctx = new WebContext(request, response, servletContext, request.getLocale());

        User user = (User) context.getActiveUser();
        if(user == null || ! user.hasFlag(UserFlag.GAMEMASTER)) {
          Error error = new Error("Du hast keine Berechtigung f&uuml;r diese Seite.","./ds?module=ueber&amp;action=default");
          ctx.setVariable("error",error);
          templateEngine.process("gamemaster", ctx, response.getWriter());
          return;
        }

        Action action = Action.DEFAULT;
        try{
         action = Action.valueOf(request.getParameter("action").toUpperCase());
        } catch(Exception ex){}
        Show show = Show.DEFAULT;
        try{
         show = Show.valueOf(request.getParameter("show").toUpperCase());
        } catch(Exception ex){}

        switch(action){
          case TICK:
            tickAction(ctx, request, user);
            break;
          case START:
            startAction(ctx, request, user);
            break;
          case END:
            endAction(ctx, request, user);
            break;
          case RELOCATE:
            relocateAction(ctx, request, user);
            break;
          default:
            defaultAction(ctx, request, user, show);
            break;
        }

        templateEngine.process("gamemaster", ctx, response.getWriter());
    }


    /**
     * Startet den Kampagnentick. Warten noch Teilnehmer, muss der GameMaster das bestaetigen.
     * @param ctx der WebContext
     * @param request der HttpServletRequest (enthaelt die uebergebenen Parameter)
     */
    private void tickAction(WebContext ctx, HttpServletRequest request, User user) throws ClassNotFoundException {

      int tick = Integer.parseInt(new ConfigService(context.getEM()).get(WellKnownConfigValue.TICK).getValue());
      if(tick == 1){
        Error error = new Error("Es l&auml;uft bereits ein Tick.", "./gamemaster");
        ctx.setVariable("error",error);
        defaultAction(ctx, request, user, Show.TICK);
        return;
      }

      String[] systems = request.getParameterValues("systemcheckbox");
      if(systems == null || systems.length == 0){
        ctx.setVariable("error", new Error("Bitte w&auml;hle mindestens ein System aus.", "./gamemaster?show=tick"));
        defaultAction(ctx, request, user, Show.TICK);
        return;
      }
      for(String system : systems){
        if(!system.matches("\\d+")){
          ctx.setVariable("error", new Error("Ung&uuml;ltiges System.", "./gamemaster?show=tick"));
          defaultAction(ctx, request, user, Show.TICK);
          return;
        }
      }

      List<User> waiting = participants(context.getEM()).stream()
          .filter(u -> !Boolean.TRUE.equals(u.isTickReady()))
          .collect(Collectors.toList());
      if(!waiting.isEmpty() && !"1".equals(request.getParameter("confirm"))){
        ctx.setVariable("error", new Error("Es sind noch nicht alle Teilnehmer bereit: "
            + waiting.stream().map(u -> HtmlUtils.htmlEscape(u.getPlainname())).collect(Collectors.joining(", "))
            + ". Best&auml;tige den Tick, um ihn trotzdem auszuf&uuml;hren.", "./gamemaster?show=tick"));
        ctx.setVariable("tickNeedsConfirm", true);
        defaultAction(ctx, request, user, Show.TICK);
        return;
      }

      new AdminCommands().executeCommand("tick campaign run "+String.join(";", systems));
      ctx.setVariable("out", "Kampagnentick für Systeme "+String.join(", ",systems)+" gestartet");
      defaultAction(ctx, request, user, Show.TICK);
    }

    /**
     * Startet die vorbereitete Kampagne. Die Teilnehmer werden vorher im Admin-Plugin
     * "Kampagne / Teilnehmer" ausgewaehlt; hier werden sie nur noch geprueft und freigegeben.
     * @param ctx der WebContext
     * @param request der HttpServletRequest (enthaelt die uebergebenen Parameter)
     */
    private void startAction(WebContext ctx, HttpServletRequest request, User user) {
      CampaignService service = new CampaignService(context.getEM());
      Optional<Campaign> campaign = service.findOpenCampaign();
      if(campaign.isEmpty() || !campaign.get().isPreparing()){
        ctx.setVariable("error", new Error("Es wird keine Kampagne vorbereitet.", "./gamemaster"));
        defaultAction(ctx, request, user, Show.START);
        return;
      }
      if(!"1".equals(request.getParameter("confirm"))){
        ctx.setVariable("error", new Error("Bitte best&auml;tige die Teilnehmerliste.", "./gamemaster?show=start"));
        defaultAction(ctx, request, user, Show.START);
        return;
      }

      try {
        service.start(campaign.get());
        ctx.setVariable("out", "Kampagne gestartet. Die Kampagnen-Sprungpunkte sind jetzt aktiv.");
      } catch(CampaignException e) {
        ctx.setVariable("error", new Error(HtmlUtils.htmlEscape(e.getMessage()), "./gamemaster?show=start"));
      }
      defaultAction(ctx, request, user, Show.START);
    }

    /**
     * Beendet die laufende Kampagne und setzt den Teilnehmer- und Bereit-Status aller Spieler zurueck.
     * @param ctx der WebContext
     * @param request der HttpServletRequest (enthaelt die uebergebenen Parameter)
     */
    private void endAction(WebContext ctx, HttpServletRequest request, User user) {
      CampaignService service = new CampaignService(context.getEM());
      Optional<Campaign> campaign = service.findOpenCampaign();
      if(!"1".equals(request.getParameter("confirm"))){
        ctx.setVariable("error", new Error("Bitte best&auml;tige das Beenden der Kampagne.", "./gamemaster?show=end"));
        defaultAction(ctx, request, user, Show.END);
        return;
      }
      try {
        service.end(campaign.orElseThrow(() -> new CampaignException("Es läuft keine Kampagne.")));
        ctx.setVariable("out", "Kampagne beendet. Teilnehmerstatus zurückgesetzt, Kampagnen-Sprungpunkte wieder vorbereitet.");
      } catch(CampaignException e) {
        ctx.setVariable("error", new Error(HtmlUtils.htmlEscape(e.getMessage()), "./gamemaster"));
      }
      defaultAction(ctx, request, user, Show.END);
    }

    /**
     * Versetzt Spielerschiffe aus den Kampagnensystemen zu einem Ausgang, bevor die Kampagne endet.
     * Parameter: ship (ID oder "all") und exit (Ziel im Format system:x/y).
     */
    private void relocateAction(WebContext ctx, HttpServletRequest request, User user) {
      var db = context.getEM();
      Optional<Campaign> campaign = new CampaignService(db).findOpenCampaign();
      try {
        if(campaign.isEmpty()){
          throw new CampaignException("Es läuft keine Kampagne.");
        }
        CampaignMapService mapService = new CampaignMapService(db);
        Location target = parseExit(mapService, campaign.get(), request.getParameter("exit"));

        String ship = request.getParameter("ship");
        List<Integer> shipIds = new ArrayList<>();
        if("all".equals(ship)){
          for(CampaignObject object : mapService.findPlayerObjects(campaign.get())){
            if(object.type == CampaignObject.Type.SHIP){
              shipIds.add(object.id);
            }
          }
        } else {
          shipIds.add(Integer.parseInt(ship));
        }

        List<String> failures = new ArrayList<>();
        int moved = 0;
        for(int shipId : shipIds){
          try {
            mapService.relocateShip(shipId, target);
            moved++;
          } catch(CampaignException e) {
            failures.add(e.getMessage());
          }
        }
        ctx.setVariable("out", moved + " Schiff(e) nach " + target.displayCoordinates(false) + " versetzt."
            + (failures.isEmpty() ? "" : " Nicht versetzt: " + String.join(" ", failures)));
      } catch(CampaignException e) {
        ctx.setVariable("error", new Error(HtmlUtils.htmlEscape(e.getMessage()), "./gamemaster?show=end"));
      } catch(NumberFormatException e) {
        ctx.setVariable("error", new Error("Ung&uuml;ltiges Schiff.", "./gamemaster?show=end"));
      }
      defaultAction(ctx, request, user, Show.END);
    }

    private static Location parseExit(CampaignMapService mapService, Campaign campaign, String exit) {
      for(JumpNode node : mapService.findExits(campaign)){
        Location out = new Location(node.getSystemOut(), node.getXOut(), node.getYOut());
        if(out.asString().equals(exit)){
          return out;
        }
      }
      throw new CampaignException("Unbekannter Ausgang.");
    }

    /**
     * Zeigt die GameMasterSeite an
     * @param ctx der WebContext
     * @param request der HttpServletRequest (enthaelt die uebergebenen Parameter)
     * @param user der User, der die Seite aufruft
     */
    private void defaultAction(WebContext ctx, HttpServletRequest request, User user, Show show){

      var db = context.getEM();
      CampaignService service = new CampaignService(db);
      CampaignMapService mapService = new CampaignMapService(db);
      Optional<Campaign> campaign = service.findOpenCampaign();
      boolean running = campaign.isPresent() && campaign.get().isRunning();
      boolean preparing = campaign.isPresent() && campaign.get().isPreparing();

      switch(show){
        case START:
          if(preparing){
            ctx.setVariable("participants", CampaignUserView.of(participants(db, service.getParticipantIds(campaign.get())), db));
            ctx.setVariable("startBlockers", service.findStartBlockers(campaign.get()));
            ctx.setVariable("foreignObjects", mapService.findForeignObjects(campaign.get()));
            ctx.setVariable("campaignSystemCount", mapService.getSystemIds(campaign.get()).size());
            ctx.setVariable("campaignJumpNodeCount", mapService.getJumpNodeStates().size());
          }
          break;
        case TICK:
        case END:
          List<User> userlist = participants(db);
          ctx.setVariable("participants", CampaignUserView.of(userlist, db));

          if(show == Show.TICK){
            // Nur Kampagnensysteme, alle vorausgewaehlt
            List<StarsystemsList> systemsViewModel = new ArrayList<>();
            if(running){
              Set<Integer> campaignSystems = mapService.getSystemIds(campaign.get());
              for (var starsystem: StarsystemRepository.getInstance().getStarsystemsData()) {
                  if(campaignSystems.contains(starsystem.id)) {
                      systemsViewModel.add(new StarsystemsList(starsystem.id, starsystem.name + " ("+ starsystem.id +")"));
                  }
              }
            }
            ctx.setVariable("starsystems", systemsViewModel);
            ctx.setVariable("allReady", userlist.stream().allMatch(u -> Boolean.TRUE.equals(u.isTickReady())));
          }
          else if(running){
            ctx.setVariable("leftBehind", mapService.findPlayerObjects(campaign.get()));
            List<ExitView> exits = new ArrayList<>();
            for(JumpNode node : mapService.findExits(campaign.get())){
              Location out = new Location(node.getSystemOut(), node.getXOut(), node.getYOut());
              exits.add(new ExitView(out.asString(), node.getName() + " → " + out.displayCoordinates(false)));
            }
            ctx.setVariable("exits", exits);
          }
          break;
        default:
          break;
      }
      ctx.setVariable("campaign", running);
      ctx.setVariable("preparing", preparing);
      ctx.setVariable("campaignName", campaign.map(Campaign::getName).orElse(null));
      ctx.setVariable("participantsPlugin", "./ds?module=admin&namedplugin=" + CampaignParticipants.class.getName());
      ctx.setVariable("show", show.name());
    }

    private static List<User> participants(EntityManager db){
      return db.createQuery("select u from User u where u.campaign_participant = true order by u.id", User.class)
          .getResultList();
    }

    private static List<User> participants(EntityManager db, Set<Integer> ids){
      if(ids.isEmpty()){
        return new ArrayList<>();
      }
      return db.createQuery("select u from User u where u.id in (:ids) order by u.id", User.class)
          .setParameter("ids", ids)
          .getResultList();
    }

    private static class Error{
      public final String text;
      public final String link;

      public Error(String text, String link){
        this.text = text;
        this.link = link;
      }
    }

    public static class ExitView
    {
        public final String value;
        public final String label;

        public ExitView(String value, String label)
        {
            this.value = value;
            this.label = label;
        }
    }

    public static class StarsystemsList
    {
        public final int id;
        public final String name;

        public StarsystemsList(int id, String name)
        {

            this.id = id;
            this.name = name;
        }
    }
}
