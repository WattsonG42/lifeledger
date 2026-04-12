package lifeledger.config;

public class ServerConfig {
    public int defaultStocks = 3;
    public String banMessage = "Lol noober";

    // Death filter: set to false to prevent that death type from removing a stock, can be modified through settings using mod menu; or just here thats fine too
    public boolean countMobDeaths = true;
    public boolean countPvpDeaths = true;
    public boolean countFallDamage = true;
    public boolean countVoidDeaths = true;
    public boolean countEnderDragonDeaths = true;
    public boolean countWitherDeaths = true;
    public boolean countElderGuardianDeaths = true;

    public boolean countExplosionDeaths = true;
    public boolean countAnvilDeaths = true;

    // Death Sentence: any item with this name marks a player; if they die within the window, a stock is lost
    public boolean deathSentenceEnabled = true;
    public String deathSentenceItemName = "Death Sentence";
    public int deathSentenceWindowSeconds = 20;
}