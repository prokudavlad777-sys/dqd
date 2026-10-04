package dev.guns;

import org.bukkit.Material;

public enum GunType {
    REVOLVER("revolver", "Револьвер", "Револьверні патрони", Material.DISC_FRAGMENT_5, Material.PRISMARINE_SHARD),
    SHOTGUN("shotgun", "Обріз", "Патрони для обріза", Material.ECHO_SHARD, Material.PRISMARINE_CRYSTALS);

    public final String id;
    public final String gunName;
    public final String ammoName;
    public final Material gunMaterial;
    public final Material ammoMaterial;

    GunType(String id, String gunName, String ammoName, Material gunMaterial, Material ammoMaterial) {
        this.id = id;
        this.gunName = gunName;
        this.ammoName = ammoName;
        this.gunMaterial = gunMaterial;
        this.ammoMaterial = ammoMaterial;
    }

    public static GunType byId(String id) {
        if (id == null) return null;
        for (GunType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    /** Розпізнає англійські та українські назви з команди. */
    public static GunType parse(String s) {
        if (s == null) return null;
        switch (s.toLowerCase()) {
            case "revolver":
            case "револьвер":
                return REVOLVER;
            case "shotgun":
            case "obriz":
            case "обріз":
            case "обрiз":
                return SHOTGUN;
            default:
                return null;
        }
    }
}
