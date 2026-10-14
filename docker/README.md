# Local instance

Serves the game on http://localhost:8080 against a MySQL container. The WAR must be built first —
**with `clean`**, or `maven-war-plugin` packages a stale exploded webapp and deleted assets reappear:

```bash
./mvnw -pl game -am clean package -DskipTests
docker compose -f docker/docker-compose.yml build app
docker compose -f docker/docker-compose.yml up -d
```

`db-init/` is auto-loaded into a *fresh* data volume: drop a `.sql`/`.sql.gz` dump in there before the
first `up`. Flyway then migrates whatever is in the database up to what the current code expects, and
runs once per `up`.

## Discord chat on the overview page

The chat box on the overview page shows channels of our Discord server (ID `471610776126226432`).
Players link their Discord account under *Einstellungen* via OAuth2 — they log in at Discord, the game
never sees their password. The game reads the channels through a bot and posts player messages
through a webhook named "Drifting Souls", which it creates per channel. Without the settings below the
chat box only shows an invite link.

1. In the [Discord Developer Portal](https://discord.com/developers/applications), create an application.
2. Under *OAuth2*, note the client ID and client secret, and add the redirect
   `<game URL>/ds?module=options&action=discordCallback` (e.g.
   `http://localhost:8080/ds?module=options&action=discordCallback`). It must match `DISCORD_REDIRECT_URI`
   exactly.
3. Under *Bot*, reset and copy the token, and enable the *Message Content Intent*. Without it, the
   messages arrive without text.
4. Invite the bot to the server:
   `https://discord.com/oauth2/authorize?client_id=<client ID>&scope=bot&permissions=536937473`.
   That is *View Channels*, *Read Message History*, *Manage Webhooks* and *Create Instant Invite*. The
   last one lets the game add players who link their account but are not on the server yet.
5. Pass the values to the `app` container as environment variables. The entrypoint writes them into
   `config.xml`:

   ```bash
   DISCORD_BOT_TOKEN=... DISCORD_CLIENT_ID=... DISCORD_CLIENT_SECRET=... \
   DISCORD_REDIRECT_URI='http://localhost:8080/ds?module=options&action=discordCallback' \
   docker compose -f docker/docker-compose.yml up -d
   ```

   Outside Docker, set the same names in `WEB-INF/cfg/config.xml` (see `config.sample.xml`).
6. In the admin area under *Konfiguration*, set `discord_channels` to the channels to offer, as
   `channelID:Name;channelID:Name` (Discord developer mode → right-click the channel → *Copy Channel ID*).
   `discord_guild_id` and `discord_invite_url` are already preset.

Every logged-in player can read the configured channels, linked or not, so only list channels meant
for everyone. Only linked players who are members of the server can write; a ban or kick on Discord
therefore also takes effect in the game, within five minutes.

## A second stack in parallel

Nothing in the compose file is named or bound absolutely, so a second checkout — typically a git
worktree for a hotfix running alongside longer-lived work — can bring up its own stack. In the second
checkout only:

```bash
git worktree add ../ds2-hotfix master        # from the main checkout
cd ../ds2-hotfix
cp docker/.env.sample docker/.env            # sets project name + ports; edit if 8081/3307 are taken
cp ../driftingsouls/docker/db-init/dump.sql.gz docker/db-init/   # optional, for real data
./mvnw -pl game -am clean package -DskipTests
docker compose -f docker/docker-compose.yml up -d --build
```

That stack gets its own containers, its own `db-data` volume and its own ports; the two share only the
Docker daemon and the images. Loading the dump again takes a while and costs a few GB of disk, so skip
it if an empty migrated schema is enough for what you are fixing.

**Do not create a `docker/.env` in the main checkout.** Without one the project name defaults to the
directory name (`docker`), which is what the existing `docker_db-data` volume — the one holding your
loaded dump — belongs to. Setting `COMPOSE_PROJECT_NAME` there would silently start from an empty
database.

Container names follow the project name, so commands that used to name containers directly become:

```bash
docker compose -f docker/docker-compose.yml exec db mysql -uroot -pds ds
docker compose -f docker/docker-compose.yml logs -f app
```

Run them from the checkout you mean, or pass `-p <project>` from anywhere.

## Tearing down

```bash
docker compose -f docker/docker-compose.yml down          # stop, keep the database
docker compose -f docker/docker-compose.yml down -v       # ...and delete its data volume
git worktree remove ../ds2-hotfix                         # once the branch is merged
```

`down -v` deletes the data volume of *that* project. Run from the wrong checkout it will happily drop
the main instance's loaded dump, so check `docker compose ls` first if in doubt.
