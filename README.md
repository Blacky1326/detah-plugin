# DeathChest

Ein Paper-Plugin (Minecraft/Paper 26.2) für einen "Death Chest": Beim Tod landen
alle verlorenen Items **und XP** sicher in einer Truhe an einem gültigen Block
in der Nähe deines Todesortes, statt am Boden zu verstreuen. Nach dem Respawn
bekommst du automatisch einen Kompass, der zu dieser Truhe zeigt.

## Funktionen

- Items werden beim Tod nicht gedroppt, sondern in eine Truhe gepackt.
- Reicht eine Truhe nicht (mehr als 27 Slots), werden automatisch weitere
  Truhen danebengesetzt.
- Verlorene XP werden gespeichert und beim ersten Öffnen der Truhe exakt
  zurückgegeben.
- Nach dem Respawn: automatischer Marker-Kompass zur Truhe.
- `/deathchest` gibt jederzeit erneut einen Marker-Kompass aus (solange die
  Truhe noch existiert).
- Nur der Besitzer darf seine eigene Truhe öffnen (konfigurierbar,
  Bypass-Permission `deathchest.bypass`).
- Leere Truhen entfernen sich automatisch selbst.

## Bauen

Voraussetzungen: JDK 21, Maven, Internetzugriff auf `repo.papermc.io`.

```bash
mvn clean package
```

Die fertige Datei liegt danach unter `target/DeathChest.jar`. Diese Datei
kannst du in den `plugins`-Ordner deines Paper-26.2-Servers legen.

## Konfiguration (`config.yml`)

```yaml
protect-chest: true       # nur Besitzer darf öffnen
remove-when-empty: true   # leere Truhe wird automatisch abgebaut
messages:
  ...                     # alle Texte frei anpassbar (& für Farbcodes)
```

## Hinweis zur Persistenz

Die Zuordnung "welche Truhe gehört zu welchem Todesort" ist direkt in der
Truhe selbst gespeichert (PersistentDataContainer) und übersteht daher auch
Server-Neustarts. Der Marker-Kompass wird beim Tod nur *im Arbeitsspeicher*
vermerkt und beim nächsten Respawn vergeben – bei einem Serverneustart
zwischen Tod und Respawn geht dieser automatische Kompass-Versand verloren.
Die Truhe und ihr Inhalt (Items + XP) bleiben davon unberührt, und mit
`/deathchest` lässt sich jederzeit ein neuer Kompass anfordern, solange die
Truhe noch existiert.
