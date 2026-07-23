# 15. Party (csapat) 👥

WoW-stílusú **csapat**: legfeljebb **5 fő**, teljesen **frakciótól függetlenül** — bármelyik
frakcióból lehet valaki a csapatodban, akár egy Piros és egy Kék is együtt kalandozhat.

## Hogyan alakíts csapatot?

1. **Hívd meg** a barátodat: `/party invite <név>` (aki meghív, az lesz a **csapatvezető**).
2. A meghívott **elfogadja**: `/party accept` (vagy elutasítja: `/party decline`).
3. Kész! A tagokat a `/party list` (vagy simán `/party`) mutatja — a vezetőt 👑 jelöli.

## Mit ad a csapat?

- **Közös XP:** a közeli mob-ölésekből járó kaszt-XP a közelben lévő párttagok közt
  **fejenként oszlik meg** — minél többen vagytok együtt, annál kisebb rész jut mindenkinek
  (igazi WoW-módra); az osztás utáni maradék a **megölőé**. Cserébe együtt sokkal gyorsabban
  és biztonságosabban öltök.
- **Personal loot az eseményeknél:** a plugin loot-eseményeinél (**Vad Hajsza**,
  **elrejtett kincs**) nem egy közös zsákmányon kell osztozni — minden közelben lévő párttag
  a **saját jutalmát** kapja. Lásd [Világesemények](10-vilagesemenyek.md).
- **Nincs PvP párton belül:** a tagok **nem tudják sebezni egymást** — sem közelharccal, sem
  nyíllal. Nyugodtan varázsolhatsz a kavarodásban.
- **Csapat-chat:** `/p <üzenet>` — csak a párttagok látják. (Hosszabb formában:
  `/party chat <üzenet>`.)

## Party-HUD 📊

Amíg csapatban vagy, a **HUD-oldalsávon** (a képernyő jobb szélén) egy **„— Csapat —" szekció**
mutatja a tagokat: név + **színkódolt élet-sáv** (zöld / sárga / piros) + szív-szám, a vezetőt
👑 jelöli. **Élőben frissül**, így harc közben is látod, kinek kell segítség — mint egy igazi
WoW party-frame. Ha nem vagy csapatban, a szekció el sem foglal helyet az oldalsávon.

## A csapatvezető jogai 👑

| Parancs | Mit csinál |
|---|---|
| `/party kick <név>` | Tag kirúgása a csapatból |
| `/party promote <név>` | A vezetés átadása egy tagnak |
| `/party disband` | A csapat feloszlatása |

## Jó tudni

- Kilépés a csapatból: `/party leave`.
- Ha valaki **kilép a szerverről**, automatikusan kikerül a csapatból.
- Ha a létszám **2 fő alá** csökken, a csapat **automatikusan feloszlik**.
- A meghívó **lejár** egy idő után — ha nem sikerült elfogadni, kérj újat.

---

[Vissza a tartalomhoz](README.md)

## Céhek — a frakción belüli kisközösséged ⚜

A frakció (50+ fő) és a party (5 fő) közé érkezik a **céh**: 10-15 fős, tartós közösség
saját névvel, kasszával és céh-szinttel.

- `/ceh letrehoz <név>` — céh-alapítás (kis díj a számládról). Csak frakciótag alapíthat.
- `/ceh meghiv <játékos>` → a meghívott `/ceh elfogad` — **csak a saját frakciódból** hívhatsz.
- `/ceh befizet <összeg>` — a számládról a **céh-kasszába** teszel (közös célokra gyűjthettek).
- `/ceh info` — szint, XP, tagok, kassza; `/ceh lista` — a szerver top-céhei.
- **Céh-szint:** a tagok quest-teljesítései céh-XP-t termelnek — szintlépéskor mindenki
  értesül, és a **taglétszám-plafon nő** (10-ről akár 15-ig). A közös munka szó szerint
  nagyobbá teszi a céhet!
- Kilépés: `/ceh elhagy` (a vezetés az első tagra száll); a vezető `/ceh kirug`-gal rúghat.
