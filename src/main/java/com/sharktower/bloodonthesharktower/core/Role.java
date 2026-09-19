package com.sharktower.bloodonthesharktower.core;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 26.2 port of BOTB's complete official role enum.
 *
 * All 182 original enum entries, team assignments and icon paths are preserved.
 * Packet codecs are intentionally deferred to the networking milestone.
 */
public enum Role {
    NO_ROLE(RoleType.NONE, "textures/roles/no_role.png"),
    CHEF(RoleType.TOWNSFOLK, "textures/roles/tb/chef.png"),
    INVESTIGATOR(RoleType.TOWNSFOLK, "textures/roles/tb/investigator.png"),
    WASHERWOMAN(RoleType.TOWNSFOLK, "textures/roles/tb/washerwoman.png"),
    LIBRARIAN(RoleType.TOWNSFOLK, "textures/roles/tb/librarian.png"),
    EMPATH(RoleType.TOWNSFOLK, "textures/roles/tb/empath.png"),
    FORTUNE_TELLER(RoleType.TOWNSFOLK, "textures/roles/tb/fortune_teller.png"),
    UNDERTAKER(RoleType.TOWNSFOLK, "textures/roles/tb/undertaker.png"),
    MONK(RoleType.TOWNSFOLK, "textures/roles/tb/monk.png"),
    SLAYER(RoleType.TOWNSFOLK, "textures/roles/tb/slayer.png"),
    SOLDIER(RoleType.TOWNSFOLK, "textures/roles/tb/soldier.png"),
    RAVENKEEPER(RoleType.TOWNSFOLK, "textures/roles/tb/ravenkeeper.png"),
    VIRGIN(RoleType.TOWNSFOLK, "textures/roles/tb/virgin.png"),
    MAYOR(RoleType.TOWNSFOLK, "textures/roles/tb/mayor.png"),
    GRANDMOTHER(RoleType.TOWNSFOLK, "textures/roles/bmr/grandmother.png"),
    SAILOR(RoleType.TOWNSFOLK, "textures/roles/bmr/sailor.png"),
    CHAMBERMAID(RoleType.TOWNSFOLK, "textures/roles/bmr/chambermaid.png"),
    INNKEEPER(RoleType.TOWNSFOLK, "textures/roles/bmr/innkeeper.png"),
    GAMBLER(RoleType.TOWNSFOLK, "textures/roles/bmr/gambler.png"),
    EXORCIST(RoleType.TOWNSFOLK, "textures/roles/bmr/exorcist.png"),
    GOSSIP(RoleType.TOWNSFOLK, "textures/roles/bmr/gossip.png"),
    COURTIER(RoleType.TOWNSFOLK, "textures/roles/bmr/courtier.png"),
    PROFESSOR(RoleType.TOWNSFOLK, "textures/roles/bmr/professor.png"),
    MINSTREL(RoleType.TOWNSFOLK, "textures/roles/bmr/minstrel.png"),
    TEA_LADY(RoleType.TOWNSFOLK, "textures/roles/bmr/tea_lady.png"),
    FOOL(RoleType.TOWNSFOLK, "textures/roles/bmr/fool.png"),
    PACIFIST(RoleType.TOWNSFOLK, "textures/roles/bmr/pacifist.png"),
    CLOCKMAKER(RoleType.TOWNSFOLK, "textures/roles/snv/clockmaker.png"),
    DREAMER(RoleType.TOWNSFOLK, "textures/roles/snv/dreamer.png"),
    SNAKE_CHARMER(RoleType.TOWNSFOLK, "textures/roles/snv/snake_charmer.png"),
    MATHEMATICIAN(RoleType.TOWNSFOLK, "textures/roles/snv/mathematician.png"),
    FLOWERGIRL(RoleType.TOWNSFOLK, "textures/roles/snv/flowergirl.png"),
    TOWN_CRIER(RoleType.TOWNSFOLK, "textures/roles/snv/town_crier.png"),
    ORACLE(RoleType.TOWNSFOLK, "textures/roles/snv/oracle.png"),
    SAVANT(RoleType.TOWNSFOLK, "textures/roles/snv/savant.png"),
    SEAMSTRESS(RoleType.TOWNSFOLK, "textures/roles/snv/seamstress.png"),
    PHILOSOPHER(RoleType.TOWNSFOLK, "textures/roles/snv/philosopher.png"),
    ARTIST(RoleType.TOWNSFOLK, "textures/roles/snv/artist.png"),
    JUGGLER(RoleType.TOWNSFOLK, "textures/roles/snv/juggler.png"),
    SAGE(RoleType.TOWNSFOLK, "textures/roles/snv/sage.png"),
    NOBLE(RoleType.TOWNSFOLK, "textures/roles/kickstarter/noble.png"),
    PIXIE(RoleType.TOWNSFOLK, "textures/roles/kickstarter/pixie.png"),
    GENERAL(RoleType.TOWNSFOLK, "textures/roles/kickstarter/general.png"),
    KING(RoleType.TOWNSFOLK, "textures/roles/kickstarter/king.png"),
    LYCANTHROPE(RoleType.TOWNSFOLK, "textures/roles/kickstarter/lycanthrope.png"),
    ENGINEER(RoleType.TOWNSFOLK, "textures/roles/kickstarter/engineer.png"),
    HUNTSMAN(RoleType.TOWNSFOLK, "textures/roles/kickstarter/huntsman.png"),
    ALCHEMIST(RoleType.TOWNSFOLK, "textures/roles/kickstarter/alchemist.png"),
    CANNIBAL(RoleType.TOWNSFOLK, "textures/roles/kickstarter/cannibal.png"),
    AMNESIAC(RoleType.TOWNSFOLK, "textures/roles/kickstarter/amnesiac.png"),
    FARMER(RoleType.TOWNSFOLK, "textures/roles/kickstarter/farmer.png"),
    CHOIRBOY(RoleType.TOWNSFOLK, "textures/roles/kickstarter/choirboy.png"),
    MAGICIAN(RoleType.TOWNSFOLK, "textures/roles/kickstarter/magician.png"),
    POPPY_GROWER(RoleType.TOWNSFOLK, "textures/roles/kickstarter/poppy_grower.png"),
    ATHEIST(RoleType.TOWNSFOLK, "textures/roles/kickstarter/atheist.png"),
    STEWARD(RoleType.TOWNSFOLK, "textures/roles/carousel/steward.png"),
    KNIGHT(RoleType.TOWNSFOLK, "textures/roles/carousel/knight.png"),
    SHUGENJA(RoleType.TOWNSFOLK, "textures/roles/carousel/shugenja.png"),
    BOUNTY_HUNTER(RoleType.TOWNSFOLK, "textures/roles/carousel/bounty_hunter.png"),
    HIGH_PRIESTESS(RoleType.TOWNSFOLK, "textures/roles/carousel/high_priestess.png"),
    BALLOONIST(RoleType.TOWNSFOLK, "textures/roles/carousel/balloonist.png"),
    PREACHER(RoleType.TOWNSFOLK, "textures/roles/carousel/preacher.png"),
    VILLAGE_IDIOT(RoleType.TOWNSFOLK, "textures/roles/carousel/village_idiot.png"),
    CULT_LEADER(RoleType.TOWNSFOLK, "textures/roles/carousel/cult_leader.png"),
    ACROBAT(RoleType.TOWNSFOLK, "textures/roles/carousel/acrobat.png"),
    ALSAAHIR(RoleType.TOWNSFOLK, "textures/roles/carousel/alsaahir.png"),
    NIGHTWATCHMAN(RoleType.TOWNSFOLK, "textures/roles/carousel/nightwatchman.png"),
    FISHERMAN(RoleType.TOWNSFOLK, "textures/roles/carousel/fisherman.png"),
    PRINCESS(RoleType.TOWNSFOLK, "textures/roles/carousel/princess.png"),
    BANSHEE(RoleType.TOWNSFOLK, "textures/roles/carousel/banshee.png"),
    BUTLER(RoleType.OUTSIDER, "textures/roles/tb/butler.png"),
    SAINT(RoleType.OUTSIDER, "textures/roles/tb/saint.png"),
    RECLUSE(RoleType.OUTSIDER, "textures/roles/tb/recluse.png"),
    DRUNK(RoleType.OUTSIDER, "textures/roles/tb/drunk.png"),
    GOON(RoleType.OUTSIDER, "textures/roles/bmr/goon.png"),
    LUNATIC(RoleType.OUTSIDER, "textures/roles/bmr/lunatic.png"),
    TINKER(RoleType.OUTSIDER, "textures/roles/bmr/tinker.png"),
    MOONCHILD(RoleType.OUTSIDER, "textures/roles/bmr/moonchild.png"),
    MUTANT(RoleType.OUTSIDER, "textures/roles/snv/mutant.png"),
    BARBER(RoleType.OUTSIDER, "textures/roles/snv/barber.png"),
    SWEETHEART(RoleType.OUTSIDER, "textures/roles/snv/sweetheart.png"),
    KLUTZ(RoleType.OUTSIDER, "textures/roles/snv/klutz.png"),
    GOLEM(RoleType.OUTSIDER, "textures/roles/kickstarter/golem.png"),
    DAMSEL(RoleType.OUTSIDER, "textures/roles/kickstarter/damsel.png"),
    SNITCH(RoleType.OUTSIDER, "textures/roles/kickstarter/snitch.png"),
    HERETIC(RoleType.OUTSIDER, "textures/roles/kickstarter/heretic.png"),
    PUZZLEMASTER(RoleType.OUTSIDER, "textures/roles/kickstarter/puzzlemaster.png"),
    HERMIT(RoleType.OUTSIDER, "textures/roles/carousel/hermit.png"),
    OGRE(RoleType.OUTSIDER, "textures/roles/carousel/ogre.png"),
    PLAGUE_DOCTOR(RoleType.OUTSIDER, "textures/roles/carousel/plague_doctor.png"),
    HATTER(RoleType.OUTSIDER, "textures/roles/carousel/hatter.png"),
    POLITICIAN(RoleType.OUTSIDER, "textures/roles/carousel/politician.png"),
    ZEALOT(RoleType.OUTSIDER, "textures/roles/carousel/zealot.png"),
    POISONER(RoleType.MINION, "textures/roles/tb/poisoner.png"),
    SPY(RoleType.MINION, "textures/roles/tb/spy.png"),
    BARON(RoleType.MINION, "textures/roles/tb/baron.png"),
    SCARLET_WOMAN(RoleType.MINION, "textures/roles/tb/scarlet_woman.png"),
    GODFATHER(RoleType.MINION, "textures/roles/bmr/godfather.png"),
    DEVILS_ADVOCATE(RoleType.MINION, "textures/roles/bmr/devils_advocate.png"),
    ASSASSIN(RoleType.MINION, "textures/roles/bmr/assassin.png"),
    MASTERMIND(RoleType.MINION, "textures/roles/bmr/mastermind.png"),
    WITCH(RoleType.MINION, "textures/roles/snv/witch.png"),
    CERENOVUS(RoleType.MINION, "textures/roles/snv/cerenovus.png"),
    PIT_HAG(RoleType.MINION, "textures/roles/snv/pit_hag.png"),
    EVIL_TWIN(RoleType.MINION, "textures/roles/snv/evil_twin.png"),
    MEZEPHELES(RoleType.MINION, "textures/roles/kickstarter/mezepheles.png"),
    FEARMONGER(RoleType.MINION, "textures/roles/kickstarter/fearmonger.png"),
    PSYCHOPATH(RoleType.MINION, "textures/roles/kickstarter/psychopath.png"),
    MARIONETTE(RoleType.MINION, "textures/roles/kickstarter/marionette.png"),
    BOOMDANDY(RoleType.MINION, "textures/roles/kickstarter/boomdandy.png"),
    HARPY(RoleType.MINION, "textures/roles/carousel/harpy.png"),
    WIZARD(RoleType.MINION, "textures/roles/carousel/wizard.png"),
    WIDOW(RoleType.MINION, "textures/roles/carousel/widow.png"),
    XAAN(RoleType.MINION, "textures/roles/carousel/xaan.png"),
    WRAITH(RoleType.MINION, "textures/roles/carousel/wraith.png"),
    SUMMONER(RoleType.MINION, "textures/roles/carousel/summoner.png"),
    GOBLIN(RoleType.MINION, "textures/roles/carousel/goblin.png"),
    VIZIER(RoleType.MINION, "textures/roles/carousel/vizier.png"),
    ORGAN_GRINDER(RoleType.MINION, "textures/roles/carousel/organ_grinder.png"),
    BOFFIN(RoleType.MINION, "textures/roles/carousel/boffin.png"),
    IMP(RoleType.DEMON, "textures/roles/tb/imp.png"),
    PUKKA(RoleType.DEMON, "textures/roles/bmr/pukka.png"),
    SHABALOTH(RoleType.DEMON, "textures/roles/bmr/shabaloth.png"),
    PO(RoleType.DEMON, "textures/roles/bmr/po.png"),
    ZOMBUUL(RoleType.DEMON, "textures/roles/bmr/zombuul.png"),
    FANG_GU(RoleType.DEMON, "textures/roles/snv/fang_gu.png"),
    VIGORMORTIS(RoleType.DEMON, "textures/roles/snv/vigormortis.png"),
    NO_DASHII(RoleType.DEMON, "textures/roles/snv/no_dashii.png"),
    VORTOX(RoleType.DEMON, "textures/roles/snv/vortox.png"),
    LEGION(RoleType.DEMON, "textures/roles/kickstarter/legion.png"),
    LLEECH(RoleType.DEMON, "textures/roles/kickstarter/lleech.png"),
    AL_HADIKHIA(RoleType.DEMON, "textures/roles/kickstarter/al_hadikhia.png"),
    RIOT(RoleType.DEMON, "textures/roles/kickstarter/riot.png"),
    LEVIATHAN(RoleType.DEMON, "textures/roles/kickstarter/leviathan.png"),
    YAGGABABBLE(RoleType.DEMON, "textures/roles/carousel/yaggababble.png"),
    LIL_MONSTA(RoleType.DEMON, "textures/roles/carousel/lil_monsta.png"),
    KAZALI(RoleType.DEMON, "textures/roles/carousel/kazali.png"),
    OJO(RoleType.DEMON, "textures/roles/carousel/ojo.png"),
    LORD_OF_TYPHON(RoleType.DEMON, "textures/roles/carousel/lord_of_typhon.png"),
    THIEF(RoleType.TRAVELER, "textures/roles/traveler/thief.png"),
    BUREAUCRAT(RoleType.TRAVELER, "textures/roles/traveler/bureaucrat.png"),
    GUNSLINGER(RoleType.TRAVELER, "textures/roles/traveler/gunslinger.png"),
    SCAPEGOAT(RoleType.TRAVELER, "textures/roles/traveler/scapegoat.png"),
    BEGGAR(RoleType.TRAVELER, "textures/roles/traveler/beggar.png"),
    APPRENTICE(RoleType.TRAVELER, "textures/roles/traveler/apprentice.png"),
    MATRON(RoleType.TRAVELER, "textures/roles/traveler/matron.png"),
    JUDGE(RoleType.TRAVELER, "textures/roles/traveler/judge.png"),
    VOUDON(RoleType.TRAVELER, "textures/roles/traveler/voudon.png"),
    BISHOP(RoleType.TRAVELER, "textures/roles/traveler/bishop.png"),
    BARISTA(RoleType.TRAVELER, "textures/roles/traveler/barista.png"),
    HARLOT(RoleType.TRAVELER, "textures/roles/traveler/harlot.png"),
    BUTCHER(RoleType.TRAVELER, "textures/roles/traveler/butcher.png"),
    DEVIANT(RoleType.TRAVELER, "textures/roles/traveler/deviant.png"),
    BONE_COLLECTOR(RoleType.TRAVELER, "textures/roles/traveler/bone_collector.png"),
    CACKLEJACK(RoleType.TRAVELER, "textures/roles/traveler/cacklejack.png"),
    GANGSTER(RoleType.TRAVELER, "textures/roles/traveler/gangster.png"),
    GNOME(RoleType.TRAVELER, "textures/roles/traveler/gnome.png"),
    ANGEL(RoleType.FABLED, "textures/roles/fabled/angel.png"),
    BUDDHIST(RoleType.FABLED, "textures/roles/fabled/buddhist.png"),
    DEUS_EX_FIASCO(RoleType.FABLED, "textures/roles/fabled/deus_ex_fiasco.png"),
    DJINN(RoleType.FABLED, "textures/roles/fabled/djinn.png"),
    DOOMSAYER(RoleType.FABLED, "textures/roles/fabled/doomsayer.png"),
    DUCHESS(RoleType.FABLED, "textures/roles/fabled/duchess.png"),
    FERRYMAN(RoleType.FABLED, "textures/roles/fabled/ferryman.png"),
    FIBBIN(RoleType.FABLED, "textures/roles/fabled/fibbin.png"),
    FIDDLER(RoleType.FABLED, "textures/roles/fabled/fiddler.png"),
    HELLS_LIBRARIAN(RoleType.FABLED, "textures/roles/fabled/hells_librarian.png"),
    REVOLUTIONARY(RoleType.FABLED, "textures/roles/fabled/revolutionary.png"),
    SENTINEL(RoleType.FABLED, "textures/roles/fabled/sentinel.png"),
    SPIRIT_OF_IVORY(RoleType.FABLED, "textures/roles/fabled/spirit_of_ivory.png"),
    TOYMAKER(RoleType.FABLED, "textures/roles/fabled/toymaker.png"),
    BIG_WIG(RoleType.LORIC, "textures/roles/loric/big_wig.png"),
    BOOTLEGGER(RoleType.LORIC, "textures/roles/loric/bootlegger.png"),
    GARDENER(RoleType.LORIC, "textures/roles/loric/gardener.png"),
    GOD_OF_UG(RoleType.LORIC, "textures/roles/loric/god_of_ug.png"),
    HINDU(RoleType.LORIC, "textures/roles/loric/hindu.png"),
    KNAVES(RoleType.LORIC, "textures/roles/loric/knaves.png"),
    POPE(RoleType.LORIC, "textures/roles/loric/pope.png"),
    STORM_CATCHER(RoleType.LORIC, "textures/roles/loric/storm_catcher.png"),
    TOR(RoleType.LORIC, "textures/roles/loric/tor.png"),
    VENTRILOQUIST(RoleType.LORIC, "textures/roles/loric/ventriloquist.png"),
    ZENOMANCER(RoleType.LORIC, "textures/roles/loric/zenomancer.png");

    public static final Set<Role> ALLOWS_DUPLICATES_AT_SETUP =
            Collections.unmodifiableSet(EnumSet.of(VILLAGE_IDIOT, LEGION));

    public static final List<Role> SELECTABLE_ROLES =
            Arrays.stream(values()).filter(role -> role != NO_ROLE).toList();

    private final RoleType type;
    private final Identifier icon;
    private final String nameKey;
    private final String abilityKey;

    Role(RoleType type, String iconPath) {
        this.type = type;
        this.icon = Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, iconPath);
        this.nameKey = "role." + BloodOnTheSharktower.MOD_ID + "." + getId();
        this.abilityKey = this.nameKey + ".ability";
    }

    public String getDescription() {
        return RoleText.get(abilityKey, "");
    }

    public String getId() {
        return name().toLowerCase(Locale.ROOT).replace("_", "");
    }

    public RoleType getType() {
        return type;
    }

    public boolean isDefaultGood() {
        return type.isDefaultGood();
    }

    public Identifier getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return RoleText.get(nameKey, humanize(name()));
    }

    public String getNameTranslationKey() {
        return nameKey;
    }

    public String getAbilityTranslationKey() {
        return abilityKey;
    }

    public static Role findById(String roleId) {
        if (roleId == null) return null;
        String normalized = roleId.toLowerCase(Locale.ROOT).replaceAll("[_\\s-]", "");
        for (Role role : values()) {
            if (role.getId().equals(normalized)) return role;
        }
        return null;
    }

    private static String humanize(String value) {
        String[] parts = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
