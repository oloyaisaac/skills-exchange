package com.example.data.db

/**
 * SKILL-001: Structured 125-Service Taxonomy Item Model
 */
data class ServiceTaxonomyItem(
    val id: String,
    val category: String, // "Construction", "Cleaning", "Technology", "Automotive", "Personal"
    val name: String,
    val skillFamily: String,
    val qualExpected: String = "CERTIFICATE_OR_EXPERIENCE",
    val primaryEligible: Boolean = true,
    val additionalEligible: Boolean = true,
    val typicalRateUgx: Double = 45000.0
)

/**
 * SKILL-001 & SKILL-002: Comprehensive 125-Service Catalog & Skill Validation Engine
 */
object ServiceTaxonomyCatalog {

    val services: List<ServiceTaxonomyItem> = listOf(
        // ==========================================
        // 1. CONSTRUCTION & TRADES (35 Services)
        // ==========================================
        // Family: Electrical
        ServiceTaxonomyItem("srv_001", "Construction", "Domestic Electrical Wiring", "Electrical", "CERTIFICATE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_002", "Construction", "Solar Panel Installation & Maintenance", "Electrical", "CERTIFICATE", true, true, 60000.0),
        ServiceTaxonomyItem("srv_003", "Construction", "Generator Servicing & Repair", "Electrical", "CERTIFICATE", true, true, 55000.0),
        ServiceTaxonomyItem("srv_004", "Construction", "Inverter & Battery Backup Setup", "Electrical", "CERTIFICATE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_005", "Construction", "Circuit Breaker & Distribution Board Repair", "Electrical", "CERTIFICATE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_006", "Construction", "Security Lighting & Floodlights Setup", "Electrical", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_007", "Construction", "CCTV Camera & Alarm Wiring", "Electrical", "CERTIFICATE", true, true, 55000.0),

        // Family: Plumbing
        ServiceTaxonomyItem("srv_008", "Construction", "Pipe Leak & Burst Repair", "Plumbing", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_009", "Construction", "Drain Unblocking & Sewer Clearing", "Plumbing", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_010", "Construction", "Water Heater & Geyser Installation", "Plumbing", "CERTIFICATE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_011", "Construction", "Overhead Water Tank & Pump Fitting", "Plumbing", "EXPERIENCE", true, true, 55000.0),
        ServiceTaxonomyItem("srv_012", "Construction", "Toilet, Sink & Shower Fixture Fitting", "Plumbing", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_013", "Construction", "Borehole & Submersible Pump Maintenance", "Plumbing", "CERTIFICATE", true, true, 65000.0),
        ServiceTaxonomyItem("srv_014", "Construction", "Rainwater Harvesting & Guttering Setup", "Plumbing", "EXPERIENCE", true, true, 40000.0),

        // Family: Carpentry & Woodwork
        ServiceTaxonomyItem("srv_015", "Construction", "Custom Furniture Making", "Carpentry", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_016", "Construction", "Door & Window Frame Fitting", "Carpentry", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_017", "Construction", "Kitchen Cabinetry & Shelving", "Carpentry", "EXPERIENCE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_018", "Construction", "Roof Truss & Timber Framing", "Carpentry", "EXPERIENCE", true, true, 55000.0),
        ServiceTaxonomyItem("srv_019", "Construction", "Wood Floor Sanding & Varnishing", "Carpentry", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_020", "Construction", "Locksmithing & Door Lock Repair", "Carpentry", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_021", "Construction", "Ceiling Board & Gypsum Installation", "Carpentry", "EXPERIENCE", true, true, 45000.0),

        // Family: Masonry & Civil
        ServiceTaxonomyItem("srv_022", "Construction", "Bricklaying & Block Foundation", "Masonry", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_023", "Construction", "Wall Plastering & Skimming", "Masonry", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_024", "Construction", "Floor & Wall Tile Laying", "Masonry", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_025", "Construction", "Compound Paving Block Installation", "Masonry", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_026", "Construction", "Concrete Slab & Beam Casting", "Masonry", "EXPERIENCE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_027", "Construction", "Perimeter Wall & Fence Construction", "Masonry", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_028", "Construction", "Terrazzo Floor Installation & Polishing", "Masonry", "EXPERIENCE", true, true, 55000.0),

        // Family: Metalwork & Welding
        ServiceTaxonomyItem("srv_029", "Construction", "Burglar Proofing & Window Grilles", "Metalwork", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_030", "Construction", "Metal Gate Fabrication & Repair", "Metalwork", "EXPERIENCE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_031", "Construction", "Arc & Gas Welding On-Site", "Metalwork", "CERTIFICATE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_032", "Construction", "Iron Sheet Roofing & Cladding", "Metalwork", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_033", "Construction", "Aluminium Window & Partition Fitting", "Metalwork", "EXPERIENCE", true, true, 45000.0),

        // Family: Painting & Finishes
        ServiceTaxonomyItem("srv_034", "Construction", "Interior & Exterior Wall Painting", "Painting", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_035", "Construction", "Waterproofing & Damp Proof Treatment", "Painting", "CERTIFICATE", true, true, 50000.0),

        // ==========================================
        // 2. CLEANING & DOMESTIC (25 Services)
        // ==========================================
        // Family: Housekeeping
        ServiceTaxonomyItem("srv_036", "Cleaning", "Deep Home & Move-In Cleaning", "Housekeeping", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_037", "Cleaning", "Routine Domestic House Cleaning", "Housekeeping", "EXPERIENCE", true, true, 25000.0),
        ServiceTaxonomyItem("srv_038", "Cleaning", "Carpet & Rug Steam Cleaning", "Housekeeping", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_039", "Cleaning", "Sofa & Upholstery Shampooing", "Housekeeping", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_040", "Cleaning", "Mattress Sanitization & Stain Removal", "Housekeeping", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_041", "Cleaning", "Curtain & Blind Washing", "Housekeeping", "EXPERIENCE", true, true, 25000.0),
        ServiceTaxonomyItem("srv_042", "Cleaning", "Post-Construction Site Clean-up", "Housekeeping", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_043", "Cleaning", "Office & Commercial Space Cleaning", "Housekeeping", "EXPERIENCE", true, true, 35000.0),

        // Family: Laundry & Fabric Care
        ServiceTaxonomyItem("srv_044", "Cleaning", "Hand Washing & Line Drying", "Laundry", "EXPERIENCE", true, true, 25000.0),
        ServiceTaxonomyItem("srv_045", "Cleaning", "Ironing & Wardrobe Organization", "Laundry", "EXPERIENCE", true, true, 25000.0),
        ServiceTaxonomyItem("srv_046", "Cleaning", "Shoe & Sneaker Deep Care Cleaning", "Laundry", "EXPERIENCE", true, true, 20000.0),
        ServiceTaxonomyItem("srv_047", "Cleaning", "Bedding & Heavy Blanket Washing", "Laundry", "EXPERIENCE", true, true, 30000.0),

        // Family: Gardening & Landscaping
        ServiceTaxonomyItem("srv_048", "Cleaning", "Lawn Mowing & Grass Trimming", "Gardening", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_049", "Cleaning", "Hedge Cutting & Tree Pruning", "Gardening", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_050", "Cleaning", "Garden Bed Planting & Weed Control", "Gardening", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_051", "Cleaning", "Compound Leaf Raking & Sweeping", "Gardening", "EXPERIENCE", true, true, 25000.0),
        ServiceTaxonomyItem("srv_052", "Cleaning", "Lawn Sprinkler System Installation", "Gardening", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_053", "Cleaning", "Organic Fertilizer & Soil Conditioning", "Gardening", "EXPERIENCE", true, true, 35000.0),

        // Family: Fumigation & Sanitation
        ServiceTaxonomyItem("srv_054", "Cleaning", "Termite & Ant Extermination", "Fumigation", "CERTIFICATE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_055", "Cleaning", "Mosquito & Bedbug Fumigation", "Fumigation", "CERTIFICATE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_056", "Cleaning", "Cockroach & Rodent Baiting", "Fumigation", "CERTIFICATE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_057", "Cleaning", "Water Tank Scrubbing & Disinfection", "Fumigation", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_058", "Cleaning", "Swimming Pool Cleaning & Balancing", "Fumigation", "CERTIFICATE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_059", "Cleaning", "Bio-Septic Tank Bacteria Treatment", "Fumigation", "CERTIFICATE", true, true, 55000.0),
        ServiceTaxonomyItem("srv_060", "Cleaning", "Compound Pressure Washing", "Housekeeping", "EXPERIENCE", true, true, 35000.0),

        // ==========================================
        // 3. TECHNOLOGY & ELECTRONICS (25 Services)
        // ==========================================
        // Family: Computer Hardware & Networking
        ServiceTaxonomyItem("srv_061", "Technology", "Laptop Screen & Keyboard Repair", "Computing", "CERTIFICATE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_062", "Technology", "Desktop PC Building & Upgrades", "Computing", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_063", "Technology", "Wi-Fi Router & Mesh Configuration", "Computing", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_064", "Technology", "Ethernet LAN Cable Trunking", "Computing", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_065", "Technology", "Operating System Reinstall & Speedup", "Computing", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_066", "Technology", "Virus, Malware & Ransomware Cleanup", "Computing", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_067", "Technology", "Data Recovery from Damaged Hard Drives", "Computing", "CERTIFICATE", true, true, 60000.0),
        ServiceTaxonomyItem("srv_068", "Technology", "Printer & Scanner Setup / Unclogging", "Computing", "EXPERIENCE", true, true, 30000.0),

        // Family: Mobile & Smart Devices
        ServiceTaxonomyItem("srv_069", "Technology", "Smartphone Screen Replacement", "MobileRepair", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_070", "Technology", "Phone Battery & Charging Port Repair", "MobileRepair", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_071", "Technology", "Water Damage Phone Decontamination", "MobileRepair", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_072", "Technology", "Tablet Screen & Glass Repair", "MobileRepair", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_073", "Technology", "Smartwatch Battery & Screen Service", "MobileRepair", "EXPERIENCE", true, true, 30000.0),

        // Family: Home Entertainment & Satellite
        ServiceTaxonomyItem("srv_074", "Technology", "DStv & Satellite Dish Alignment", "AudioVisual", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_075", "Technology", "Wall-Mount TV Installation", "AudioVisual", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_076", "Technology", "Home Theater & Soundbar Setup", "AudioVisual", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_077", "Technology", "LED / OLED TV Motherboard Repair", "AudioVisual", "CERTIFICATE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_078", "Technology", "Projector & Screen Conference Setup", "AudioVisual", "EXPERIENCE", true, true, 40000.0),

        // Family: Appliance Repair
        ServiceTaxonomyItem("srv_079", "Technology", "Refrigerator Compressor & Gas Refill", "ApplianceRepair", "CERTIFICATE", true, true, 55000.0),
        ServiceTaxonomyItem("srv_080", "Technology", "Washing Machine Drum & Pump Fix", "ApplianceRepair", "CERTIFICATE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_081", "Technology", "Microwave & Oven Electronic Repair", "ApplianceRepair", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_082", "Technology", "Air Conditioner Servicing & Re-Gassing", "ApplianceRepair", "CERTIFICATE", true, true, 60000.0),
        ServiceTaxonomyItem("srv_083", "Technology", "Electric Cooker & Hob Repair", "ApplianceRepair", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_084", "Technology", "Water Dispenser Sanitization & Repair", "ApplianceRepair", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_085", "Technology", "Generator Automatic Transfer Switch (ATS)", "Computing", "CERTIFICATE", true, true, 65000.0),

        // ==========================================
        // 4. AUTOMOTIVE & MOTORCYCLE (20 Services)
        // ==========================================
        // Family: Auto Mechanics
        ServiceTaxonomyItem("srv_086", "Automotive", "Brake Pad Replacement & Bleeding", "VehicleRepair", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_087", "Automotive", "Engine Oil & Filter Service (Home Visit)", "VehicleRepair", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_088", "Automotive", "Spark Plug & Fuel Injector Cleaning", "VehicleRepair", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_089", "Automotive", "Suspension Bush & Shock Absorber Fitting", "VehicleRepair", "EXPERIENCE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_090", "Automotive", "Clutch Plate & Master Cylinder Fix", "VehicleRepair", "EXPERIENCE", true, true, 55000.0),
        ServiceTaxonomyItem("srv_091", "Automotive", "Radiator Flush & Overheating Repair", "VehicleRepair", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_092", "Automotive", "Car OBD-II Computer Diagnostics", "VehicleRepair", "CERTIFICATE", true, true, 40000.0),

        // Family: Auto Electrical & AC
        ServiceTaxonomyItem("srv_093", "Automotive", "Car Battery Testing & Replacement", "AutoElectrical", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_094", "Automotive", "Alternator & Starter Motor Rebuild", "AutoElectrical", "CERTIFICATE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_095", "Automotive", "Car Air Conditioning Gas & Leak Fix", "AutoElectrical", "CERTIFICATE", true, true, 55000.0),
        ServiceTaxonomyItem("srv_096", "Automotive", "Headlight & Tail-Light Bulb Wiring", "AutoElectrical", "EXPERIENCE", true, true, 25000.0),
        ServiceTaxonomyItem("srv_097", "Automotive", "Car Alarm & GPS Tracker Installation", "AutoElectrical", "CERTIFICATE", true, true, 50000.0),

        // Family: Motorcycle (Boda Boda) Service
        ServiceTaxonomyItem("srv_098", "Automotive", "Motorcycle Engine Oil & Carburetor Tune", "BodaService", "EXPERIENCE", true, true, 25000.0),
        ServiceTaxonomyItem("srv_099", "Automotive", "Motorcycle Chain, Sprocket & Brake Pad", "BodaService", "EXPERIENCE", true, true, 25000.0),
        ServiceTaxonomyItem("srv_100", "Automotive", "Motorcycle Tyre Tube Puncture Repair", "BodaService", "EXPERIENCE", true, true, 15000.0),
        ServiceTaxonomyItem("srv_101", "Automotive", "Motorcycle Electrical & Battery Fix", "BodaService", "EXPERIENCE", true, true, 25000.0),

        // Family: Auto Detailing
        ServiceTaxonomyItem("srv_102", "Automotive", "Mobile Car Foam Wash & Vacuum", "AutoDetailing", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_103", "Automotive", "Car Body Scratch Buffing & Waxing", "AutoDetailing", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_104", "Automotive", "Car Interior Deep Shampoo & Leather Care", "AutoDetailing", "EXPERIENCE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_105", "Automotive", "Headlight Restoration & UV Coating", "AutoDetailing", "EXPERIENCE", true, true, 30000.0),

        // ==========================================
        // 5. PERSONAL, EVENT & LESSONS (20 Services)
        // ==========================================
        // Family: Beauty & Grooming
        ServiceTaxonomyItem("srv_106", "Personal", "Men's Doorstep Haircut & Beard Trim", "BeautyGrooming", "EXPERIENCE", true, true, 25000.0),
        ServiceTaxonomyItem("srv_107", "Personal", "Women's Braiding, Dreadlocks & Weaving", "BeautyGrooming", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_108", "Personal", "Doorstep Manicure & Pedicure Gel Service", "BeautyGrooming", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_109", "Personal", "Bridal & Event Makeup Application", "BeautyGrooming", "EXPERIENCE", true, true, 60000.0),
        ServiceTaxonomyItem("srv_110", "Personal", "Therapeutic Full Body Massage at Home", "BeautyGrooming", "CERTIFICATE", true, true, 65000.0),

        // Family: Academic Tutoring & Lessons
        ServiceTaxonomyItem("srv_111", "Personal", "Primary School Mathematics Tutoring", "Tutoring", "CERTIFICATE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_112", "Personal", "Secondary O-Level Science & Physics", "Tutoring", "CERTIFICATE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_113", "Personal", "English Reading & Literacy Coaching", "Tutoring", "CERTIFICATE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_114", "Personal", "Luganda / Swahili Language Lessons", "Tutoring", "EXPERIENCE", true, true, 30000.0),
        ServiceTaxonomyItem("srv_115", "Personal", "Beginner Piano & Keyboard Coaching", "Tutoring", "EXPERIENCE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_116", "Personal", "Acoustic Guitar Lessons for Kids", "Tutoring", "EXPERIENCE", true, true, 35000.0),
        ServiceTaxonomyItem("srv_117", "Personal", "Kids Swimming Instruction", "Tutoring", "CERTIFICATE", true, true, 45000.0),

        // Family: Catering & Event Assistance
        ServiceTaxonomyItem("srv_118", "Personal", "Private Home Chef & Meal Prep", "CateringEvents", "EXPERIENCE", true, true, 45000.0),
        ServiceTaxonomyItem("srv_119", "Personal", "Event Barbecue & Roasting Specialist", "CateringEvents", "EXPERIENCE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_120", "Personal", "Custom Birthday & Wedding Cake Baking", "CateringEvents", "EXPERIENCE", true, true, 50000.0),
        ServiceTaxonomyItem("srv_121", "Personal", "Event Sound System & PA DJ Setup", "CateringEvents", "EXPERIENCE", true, true, 60000.0),
        ServiceTaxonomyItem("srv_122", "Personal", "Event Photography & Portrait Shoots", "CateringEvents", "EXPERIENCE", true, true, 55000.0),
        ServiceTaxonomyItem("srv_123", "Personal", "Event Videography & Highlight Reel", "CateringEvents", "EXPERIENCE", true, true, 60000.0),

        // Family: Fitness & Well-being
        ServiceTaxonomyItem("srv_124", "Personal", "Home Personal Fitness Training & Calisthenics", "Fitness", "CERTIFICATE", true, true, 40000.0),
        ServiceTaxonomyItem("srv_125", "Personal", "Outdoor Yoga & Stretching Sessions", "Fitness", "CERTIFICATE", true, true, 40000.0)
    )

    /**
     * SKILL-002: Related Skill Validation Engine
     * Enforces that a worker's additional skills (up to 2) belong to the same skill family
     * or compatible trade family as their chosen Primary Skill.
     */
    fun validateWorkerSkills(primarySkillNameOrId: String, additionalSkillNamesOrIds: List<String>): SkillValidationResult {
        if (additionalSkillNamesOrIds.size > 2) {
            return SkillValidationResult(
                isValid = false,
                errorMessage = "Workers may select a maximum of 2 additional skills alongside their primary skill."
            )
        }

        val primary = findService(primarySkillNameOrId)
            ?: return SkillValidationResult(
                isValid = false,
                errorMessage = "Primary skill '$primarySkillNameOrId' not found in 125-service catalog."
            )

        val incompatibleSkills = mutableListOf<String>()

        for (addSkill in additionalSkillNamesOrIds) {
            val item = findService(addSkill)
            if (item == null) {
                incompatibleSkills.add("$addSkill (unknown skill)")
                continue
            }

            // Must match same category and same skill family (or compatible cross-trade family)
            val isSameFamily = item.skillFamily.equals(primary.skillFamily, ignoreCase = true)
            val isCompatibleCrossFamily = areFamiliesCompatible(primary.skillFamily, item.skillFamily)

            if (!isSameFamily && !isCompatibleCrossFamily) {
                incompatibleSkills.add("${item.name} [Family: ${item.skillFamily}]")
            }
        }

        return if (incompatibleSkills.isEmpty()) {
            SkillValidationResult(isValid = true, errorMessage = null)
        } else {
            SkillValidationResult(
                isValid = false,
                errorMessage = "Additional skills must belong to the same trade family (${primary.skillFamily}) as your primary skill (${primary.name}). Incompatible: ${incompatibleSkills.joinToString(", ")}"
            )
        }
    }

    fun findService(nameOrId: String): ServiceTaxonomyItem? {
        return services.find {
            it.id.equals(nameOrId, ignoreCase = true) ||
            it.name.equals(nameOrId, ignoreCase = true)
        }
    }

    fun getServicesByCategory(category: String): List<ServiceTaxonomyItem> {
        return services.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun getServicesByFamily(family: String): List<ServiceTaxonomyItem> {
        return services.filter { it.skillFamily.equals(family, ignoreCase = true) }
    }

    private fun areFamiliesCompatible(familyA: String, familyB: String): Boolean {
        // Defined compatible crossover trades (e.g. Electrical + Computing networking, Carpentry + Painting)
        val compatiblePairs = setOf(
            setOf("Electrical", "Computing"),
            setOf("Electrical", "ApplianceRepair"),
            setOf("Carpentry", "Painting"),
            setOf("Carpentry", "Masonry"),
            setOf("VehicleRepair", "AutoElectrical"),
            setOf("VehicleRepair", "BodaService"),
            setOf("Housekeeping", "Laundry"),
            setOf("Housekeeping", "Gardening"),
            setOf("BeautyGrooming", "Fitness"),
            setOf("CateringEvents", "Personal")
        )
        return compatiblePairs.contains(setOf(familyA, familyB))
    }
}

data class SkillValidationResult(
    val isValid: Boolean,
    val errorMessage: String?
)
