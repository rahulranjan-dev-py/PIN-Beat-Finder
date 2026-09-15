package com.pinbeatfinder.domain.model

/** Kind of post office a locality is served by. Ordered as shown in the editor dropdown. */
enum class OfficeType(val code: String, val fullName: String) {
    GPO("GPO", "General Post Office"),
    HO("HO", "Head Post Office"),
    IDC("IDC", "IDC"),
    SO("SO", "Sub Post Office"),
    BO("BO", "Branch Office"),
    ;

    companion object {
        /** Accepts codes and common spellings: "B.O", "Branch Office", "Sub Post Office", "PO"… */
        fun parse(raw: String?): OfficeType? {
            val v = raw?.trim()?.uppercase()?.replace(".", "")?.replace(Regex("\\s+"), " ") ?: return null
            if (v.isEmpty()) return null
            return when {
                v == "GPO" || v.contains("GENERAL") -> GPO
                v == "HO" || v == "HPO" || v.contains("HEAD") -> HO
                v == "IDC" -> IDC
                v == "SO" || v == "PO" || v.contains("SUB") -> SO
                v == "BO" || v.contains("BRANCH") -> BO
                else -> null
            }
        }

        /** Maps directory office-type labels/codes (BO, PO, HO, "Sub Post Office"…) with a GPO name check. */
        fun fromDirectory(typeCodeOrLabel: String, officeName: String): OfficeType =
            if (officeName.trim().uppercase().endsWith("GPO")) GPO else parse(typeCodeOrLabel) ?: SO

        /** Strips a trailing type marker ("Rampur B.O" -> "Rampur"). */
        fun stripSuffix(name: String): String =
            name.replace(Regex("""\s*\(?\b(B\.?O|S\.?O|H\.?O|G\.?P\.?O|P\.?O|IDC|MDG)\.?\)?\s*$""", RegexOption.IGNORE_CASE), "").trim().ifEmpty { name.trim() }
    }
}
