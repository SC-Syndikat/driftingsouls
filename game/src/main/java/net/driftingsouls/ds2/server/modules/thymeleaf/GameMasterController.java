package net.driftingsouls.ds2.server.modules.thymeleaf;

import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.campaign.GameMasterControl;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequest;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequestRepository;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.modules.admin.CampaignRequests;
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
import net.driftingsouls.ds2.server.modules.admin.GameMasterMenu;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.framework.Common;
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
      CONTROL,
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

        // While the game master steers an NPC, he is the one behind the session
        User user = (User) GameMasterControl.realUser();
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
          case CONTROL:
            if(controlAction(ctx, request, response)){
              return;
            }
            defaultAction(ctx, request, user, show);
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
        defaultAction(ctx, request, user, Show.START);
        return;
      }
      // Once started, the campaign is run from the tick view
      defaultAction(ctx, request, user, Show.TICK);
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
     * Steuert ein teilnehmendes NPC (oder wieder den eigenen Account) und springt zur gewuenschten Ansicht.
     * @return <code>true</code>, wenn weitergeleitet wurde
     */
    private boolean controlAction(WebContext ctx, HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
      int userId;
      try {
        userId = Integer.parseInt(request.getParameter("user"));
      } catch(NumberFormatException e) {
        ctx.setVariable("error", new Error("Ung&uuml;ltiger Account.", "./gamemaster"));
        return false;
      }
      try {
        GameMasterControl.switchTo(userId);
      } catch(CampaignException e) {
        ctx.setVariable("error", new Error(HtmlUtils.htmlEscape(e.getMessage()), "./gamemaster"));
        return false;
      }
      response.sendRedirect(controlTarget(request.getParameter("to")));
      return true;
    }

    /**
     * Only the known views; the parameter must not lead anywhere else.
     */
    private static String controlTarget(String to) {
      if(to == null) {
        return "./gamemaster";
      }
      String[] parts = to.split(":", 2);
      int id = 0;
      if(parts.length == 2) {
        try {
          id = Integer.parseInt(parts[1]);
        } catch(NumberFormatException e) {
          return "./gamemaster";
        }
      }
      switch(parts[0]) {
        case "ships":
          return "./ds?module=schiffe";
        case "bases":
          return "./ds?module=basen";
        case "ship":
          return "./ds?module=schiff&ship=" + id;
        case "base":
          return "./base?col=" + id;
        case "battle":
          return "./ds?module=angriff&battle=" + id;
        default:
          return "./gamemaster";
      }
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
      ctx.setVariable("gmMenu", GameMasterMenu.ENTRIES);
      ctx.setVariable("show", show.name());
      ctx.setVariable("gmRequests", running ? RequestView.of(db, campaign.get()) : List.of());
      ctx.setVariable("gmBattles", running ? BattleView.of(db, mapService.getSystemIds(campaign.get())) : List.of());
      ctx.setVariable("requestsPlugin", "./ds?module=admin&namedplugin=" + CampaignRequests.class.getName() + "&" + GameMasterMenu.PARAMETER + "=1");
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

    /**
     * An open request of a player, for the overview on the start page.
     */
    public static class RequestView
    {
        public final int id;
        public final String player;
        public final String dialog;
        public final String excerpt;
        public final String updated;
        public final boolean unread;
        public final String url;

        RequestView(int id, String player, String dialog, String excerpt, String updated, boolean unread, String url)
        {
            this.id = id;
            this.player = player;
            this.dialog = dialog;
            this.excerpt = excerpt;
            this.updated = updated;
            this.unread = unread;
            this.url = url;
        }

        static List<RequestView> of(EntityManager db, Campaign campaign)
        {
            GmRequestRepository requests = new GmRequestRepository();
            InteractionRepository interactions = new InteractionRepository();
            List<RequestView> views = new ArrayList<>();
            for (GmRequest request : requests.findForCampaign(campaign.getId(), true))
            {
                User player = db.find(User.class, request.userId);
                List<GmRequest.Message> messages = requests.findMessages(request.id);
                String text = messages.isEmpty() ? "" : messages.get(messages.size() - 1).text;
                views.add(new RequestView(request.id,
                    (player == null ? "?" : player.getPlainname()) + " (" + request.userId + ")",
                    Optional.ofNullable(request.interactionId).flatMap(interactions::find).map(i -> i.label).orElse("?"),
                    text.length() > 120 ? text.substring(0, 120) + "…" : text,
                    Common.date("d.m. H:i", request.updatedAt),
                    request.unreadByGm,
                    "./ds?module=admin&namedplugin=" + CampaignRequests.class.getName() + "&request=" + request.id + "&" + GameMasterMenu.PARAMETER + "=1"));
            }
            return views;
        }
    }

    /**
     * A battle in a campaign star system, for the overview on the start page.
     */
    public static class BattleView
    {
        public final int id;
        public final String location;
        public final String side0;
        public final String side1;
        public final String url;
        public final String linkLabel;

        BattleView(int id, String location, String side0, String side1, String url, String linkLabel)
        {
            this.id = id;
            this.location = location;
            this.side0 = side0;
            this.side1 = side1;
            this.url = url;
            this.linkLabel = linkLabel;
        }

        static List<BattleView> of(EntityManager db, Set<Integer> systems)
        {
            List<BattleView> views = new ArrayList<>();
            if (systems.isEmpty())
            {
                return views;
            }
            Set<Integer> npcs = GameMasterControl.controllableNpcs().stream().map(User::getId).collect(Collectors.toSet());
            for (Battle battle : db.createQuery("from Battle where system in (:systems) order by id", Battle.class)
                .setParameter("systems", systems).getResultList())
            {
                String url = "./ds?module=angriff&battle=" + battle.getId();
                String label = "ansehen";
                for (int side = 0; side < 2; side++)
                {
                    User commander = battle.getCommander(side);
                    if (commander != null && npcs.contains(commander.getId()))
                    {
                        // Seen through the NPC's eyes, which the game master may steer
                        url = GameMasterMenu.controlUrl(commander.getId(), "battle:" + battle.getId());
                        label = "als " + commander.getPlainname() + " ansehen";
                        break;
                    }
                }
                views.add(new BattleView(battle.getId(), battle.getLocation().displayCoordinates(false),
                    side(db, battle, 0), side(db, battle, 1), url, label));
            }
            return views;
        }

        private static String side(EntityManager db, Battle battle, int side)
        {
            User commander = battle.getCommander(side);
            Long ships = db.createQuery("select count(*) from BattleShip where battle = :battle and side = :side", Long.class)
                .setParameter("battle", battle)
                .setParameter("side", side)
                .getSingleResult();
            return (commander == null ? "?" : commander.getPlainname() + " (" + commander.getId() + ")") + ", " + ships + " Schiff(e)";
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
