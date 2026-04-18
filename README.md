# LifeLedger
A Fabric Minecraft mod that gives each player a finite amount of lives, called "stocks". Lose them all and you're permanently banned. No exceptions.

Built to test my theory that having meaningful deaths is a positive impact on the typical 2-week SMP to increase longevity and meaningfull gameplay. 

---

## How it works

Every player is assigned a stock count when they first join. Each death removes one stock. Hit zero and the server bans you. Stock data is tied to UUID and is saved in config/lifeledger-stocks.json there is no natural way to get them back unless an admin intervines and gives you more.

---

## Death filtering
Not every death has to count. Each death type can be toggled independently:

  | Death Type       | Toggle              |
  |------------------|---------------------|
  | Mob kills        | `countMobDeaths`    |
  | PvP              | `countPvpDeaths`    |
  | Fall damage      | `countFallDamage`   |
  | Void             | `countVoidDeaths`   |
  | Explosions       | `countExplosionDeaths` |
  | Anvil            | `countAnvilDeaths`  |
  | Ender Dragon     | `countEnderDragonDeaths` |
  | Wither           | `countWitherDeaths` |
  | Elder Guardian   | `countElderGuardianDeaths` |

Every toggelable is visible when using the mod as a client, the death check runs on a general hierarcy for example disabling MobDeaths but having Wither, Ender Dragon or Elder Guardian deaths enabled will override MobDeaths for its specific case. (the Wither status effect from unrelated sources doesnt count for its case either)

If you come across something you feel like i should add here let me know, or push it yourself on github.


---

## Death Sentence

The check for PvP deaths is extremely direct (checks source.getEntity()) by design to prevent gray zone deaths from counting we dont want to stop players from doing funny buisness "friendly" cliff pushing, tnt traps all that. We solve this by introducing and enforcing player intent. This is handled through "Death sentence". 

Rename ANY item to the configured trigger name (default: '"Death Sentence"') (not case sensitive) and brutaly punch your friend; they are now **marked**. If they die within the configured time windows from *any* cause a stock is lost regardless of what the death filter says.

The mark presists across melee hits, projectiles, TNT, and End Crystal detonations and is refreshed by continued aggression from any source originating from the player who applied it. It clears if it times out, on death or disconnect (might remove this disconnect thing to disincentive abuse).

The idea is again to let players express intent: if you're actively bloodthirsthy and want to see someone loose a stock, toggling off PvP deaths shouldn't save them.

The time window is 20 by default, and can be changed using
``
/lifeledger config deathsentencewindow <seconds>
``

---

## Tab list Display

Players can optionally see everyone's remaning stocks as little heart icons directly in the tab list (with names and ping and such). Full hearts = remaining stocks, grey hearts = expended stocks. Capped at 10 displayed. Toggled per-client and requires the mod on the client side aswell.

---

## Configuration

The mod is inherently server-authoritative. The server config lives at 'config/lifeledger.json' Admins can change settings at runtime via:

- **Mod Menu** A built-in config screen accessible through the Mod Menu mod (optional, client side) . OPs see editable toggles; non-OPs see read only so they know whats off or on. 
- **In-game commands** '/lifeledger config <key> <true|false>' for all death filter toggle.

**Non-op commands:**: <br>
/lifeledger stocks (name) - checks and outputs the stock count of the player, if no name is given it checks the calling player's stock count 

**Admin commands:** <br>
/lifeledger default              - set default stocks for new players <br>
/lifeledger set                  - set a specific player's stocks <br>
/lifeledger give                 - add stocks to a player <br>
/lifeledger take                 - remove stocks from a player <br>
/lifeledger get                  - check a player's current stocks <br>
/lifeledger config  (true|false) - toggle a death filter settings <br>
/lifeledger config deathsentencewindow - Sets the time window for death sentence
