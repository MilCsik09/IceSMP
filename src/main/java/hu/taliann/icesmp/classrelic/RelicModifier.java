package hu.taliann.icesmp.classrelic;

/**
 * A Class Power számítás közben lekérdezhető modifier-csatornái. A későbbi class rework
 * ide vesz fel új csatornát; a fogyasztók (pl. resource-max számítás) csatornán kérdeznek,
 * soha nem relic-id-n.
 */
public enum RelicModifier {
    /** A kaszt-erőforrás maximumának szorzó-bónusza (percent → 1.0 + p/100). */
    CLASS_RESOURCE_MAX,
    /** A kaszt-erőforrás visszatöltődésének szorzó-bónusza (jövőbeli fogyasztó). */
    CLASS_RESOURCE_REGEN
}
