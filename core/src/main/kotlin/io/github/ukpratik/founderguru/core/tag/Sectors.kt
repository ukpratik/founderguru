// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.tag

/**
 * The 20 What's-hot sectors (US-006, LLD §4.7). `stories.sectors` stores `1L shl ordinal`, so entries are
 * **append-only**: never reorder, rename the constant or remove one.
 */
enum class Sector(val displayName: String) {
    FINTECH("Fintech"),
    AI("AI"),
    SAAS_B2B("SaaS/B2B"),
    D2C_ECOMMERCE("D2C/E-commerce"),
    CLIMATE_ENERGY("Climate/Energy"),
    HEALTH("Health"),
    EDTECH("EdTech"),
    MOBILITY_EV("Mobility/EV"),
    LOGISTICS("Logistics"),
    AGRITECH("Agritech"),
    GAMING("Gaming"),
    CRYPTO_WEB3("Crypto/Web3"),
    DEEPTECH_SEMIS("Deeptech/Semis"),
    MEDIA_CREATOR("Media/Creator"),
    PROPTECH("Proptech"),
    HR_FUTURE_OF_WORK("HR/Future of work"),
    CYBERSECURITY("Cybersecurity"),
    SPACE("Space"),
    BIOTECH("Biotech"),
    FOOD_QUICK_COMMERCE("Food & quick commerce"),
    ;

    val bit: Long get() = 1L shl ordinal

    companion object {
        fun fromMask(mask: Long): List<Sector> = entries.filter { mask and it.bit != 0L }
    }
}

/** Keyword lists per sector, whole-word; acronyms are case-sensitive (US-006). */
object SectorMatcher {
    private val rules: Map<Sector, WordMatcher> = mapOf(
        Sector.FINTECH to WordMatcher(
            listOf(
                "fintech", "fintechs", "payments?", "neobanks?", "digital bank(?:s|ing)?", "lending", "lender", "credit cards?",
                "insurtech", "wealthtech", "remittances?", "BNPL", "buy now,? pay later", "stablecoin payments", "UPI",
                "expense cards?", "microfinance",
            ),
            listOf("BNPL", "UPI", "NBFC"),
        ),
        Sector.AI to WordMatcher(
            listOf("artificial intelligence", "machine learning", "generative AI", "AI agents?", "agentic", "large language models?", "chatbots?"),
            listOf("AI", "GenAI", "LLM", "LLMs", "AGI"),
        ),
        Sector.SAAS_B2B to WordMatcher(
            listOf("enterprise software", "B2B software", "software-as-a-service", "developer tools?", "dev ?tools", "CRM", "ERP"),
            listOf("SaaS", "B2B", "CRM", "ERP"),
        ),
        Sector.D2C_ECOMMERCE to WordMatcher(
            listOf("e-?commerce", "online retail", "marketplaces?", "retailtech", "direct-to-consumer", "consumer brands?"),
            listOf("D2C", "DTC"),
        ),
        Sector.CLIMATE_ENERGY to WordMatcher(
            listOf(
                "climate", "climatetech", "clean ?tech", "clean energy", "renewables?", "renewable energy", "solar", "wind power",
                "batter(?:y|ies)", "carbon", "decarboni[sz]ation", "net[- ]zero", "zero-emission", "green hydrogen", "energy storage",
            ),
        ),
        Sector.HEALTH to WordMatcher(
            listOf(
                "health ?tech", "healthcare", "digital health", "telehealth", "telemedicine", "medtech", "mental health",
                "patients?", "clinics?", "hospitals?", "medical", "wellness",
            ),
        ),
        Sector.EDTECH to WordMatcher(
            listOf("ed-?tech", "education", "e-learning", "online learning", "upskilling", "learning platform", "students?", "schools?"),
        ),
        Sector.MOBILITY_EV to WordMatcher(
            listOf("electric vehicles?", "mobility", "ride[- ]?hailing", "ride[- ]?pooling", "e-?scooters?", "e-?bikes?", "autonomous vehicles?", "self-driving", "e-moto"),
            listOf("EV", "EVs"),
        ),
        Sector.LOGISTICS to WordMatcher(
            listOf("logistics", "supply chains?", "freight", "shipping", "last-mile", "trucking", "warehous(?:e|es|ing)", "fleet"),
        ),
        Sector.AGRITECH to WordMatcher(
            listOf("agri-?tech", "ag-?tech", "agricultur(?:e|al)", "farmers?", "farming", "farms?", "crops?"),
        ),
        Sector.GAMING to WordMatcher(
            listOf("gaming", "video games?", "game studios?", "esports", "real[- ]money gaming"),
            listOf("RMG"),
        ),
        Sector.CRYPTO_WEB3 to WordMatcher(
            listOf("crypto", "cryptocurrenc(?:y|ies)", "blockchain", "stablecoins?", "web3", "bitcoin", "ethereum", "tokeni[sz]ation", "DeFi"),
            listOf("DeFi", "NFTs?"),
        ),
        Sector.DEEPTECH_SEMIS to WordMatcher(
            listOf("deep ?tech", "semiconductors?", "chips?", "chipmakers?", "quantum", "robotics", "robots?", "photonics", "drones?"),
            listOf("GPU", "GPUs"),
        ),
        Sector.MEDIA_CREATOR to WordMatcher(
            listOf("creator economy", "creators?", "influencers?", "media", "streaming", "podcasts?", "newsletters?", "adtech", "advertising"),
        ),
        Sector.PROPTECH to WordMatcher(
            listOf("prop-?tech", "real estate", "property", "properties", "housing", "rental", "construction tech", "contech"),
        ),
        Sector.HR_FUTURE_OF_WORK to WordMatcher(
            listOf("HR ?tech", "hiring", "recruit(?:ing|ment|er|ers)", "payroll", "remote work", "workforce", "talent", "layoffs?", "retrenchments?", "employees?"),
            listOf("HR"),
        ),
        Sector.CYBERSECURITY to WordMatcher(
            listOf("cyber ?security", "cyber", "cyberthreats?", "hackers?", "hacks?", "breach(?:es)?", "ransomware", "malware", "fraud"),
        ),
        Sector.SPACE to WordMatcher(
            listOf("space ?tech", "spacecraft", "satellites?", "rockets?", "launch vehicles?", "orbit(?:al)?", "aerospace"),
            listOf("NASA", "ISRO"),
        ),
        Sector.BIOTECH to WordMatcher(
            listOf("biotech", "biotechnology", "drug discovery", "therapeutics", "genomics", "life sciences", "pharma", "biolog(?:y|ics)"),
        ),
        Sector.FOOD_QUICK_COMMERCE to WordMatcher(
            listOf("food ?tech", "food delivery", "quick commerce", "q-commerce", "grocer(?:y|ies)", "restaurants?", "dark stores?", "beverages?"),
        ),
    )

    fun sectors(text: String): Long = rules.entries.fold(0L) { acc, (sector, m) -> if (m.matches(text)) acc or sector.bit else acc }
}
