-- R04-P2-02: the version-11 baseline used to prove the whole migration chain.
--
-- Room never exported a schema before version 27, so no historical JSON exists. This file was
-- reconstructed from the migration code itself: the tables that MIGRATION_13_14 recreates are
-- reproduced exactly as that migration wrote them (before it lost `photos`/`scheduledAt`), the
-- post-11 tables the later migrations create are absent, and every column a later migration adds
-- is absent. The test that consumes it (`DatabaseMigrationChainTest`) is what keeps it honest:
-- after the chain runs, Room validates the result against the v27 entity definitions, so any
-- drift between this baseline and the code fails the build.
--
-- Statements are separated by a semicolon, and no statement contains a literal one inside a
-- string.
CREATE TABLE IF NOT EXISTS skill_endorsements (
                        id TEXT PRIMARY KEY NOT NULL,
                        providerId TEXT NOT NULL,
                        skillName TEXT NOT NULL,
                        endorsedByNeighborName TEXT NOT NULL,
                        neighborDistrict TEXT NOT NULL
                    );
CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT PRIMARY KEY NOT NULL,
                        jobId TEXT NOT NULL,
                        senderName TEXT NOT NULL,
                        isFromUser INTEGER NOT NULL,
                        message TEXT NOT NULL,
                        timestampText TEXT NOT NULL,
                        isSystemNotification INTEGER NOT NULL
                    );
CREATE TABLE IF NOT EXISTS job_posts (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        description TEXT NOT NULL,
                        postedByResident TEXT NOT NULL,
                        district TEXT NOT NULL,
                        budget REAL NOT NULL,
                        urgency TEXT NOT NULL,
                        status TEXT NOT NULL,
                        assignedProviderId TEXT,
                        assignedProviderName TEXT,
                        createdTimestamp INTEGER NOT NULL,
                        estimatedHours REAL NOT NULL,
                        materialsCost REAL NOT NULL,
                        safetyFee REAL NOT NULL,
                        materialsProvidedBy TEXT NOT NULL DEFAULT 'RESIDENT',
                        laborHours REAL NOT NULL DEFAULT 2.0,
                        laborRate REAL NOT NULL DEFAULT 60.0,
                        laborTotal REAL NOT NULL DEFAULT 120.0,
                        materialsSubtotal REAL NOT NULL DEFAULT 0.0,
                        platformFee REAL NOT NULL DEFAULT 12.0,
                        grandTotal REAL NOT NULL DEFAULT 132.0,
                        materialReceiptsCsv TEXT NOT NULL DEFAULT '',
                        beforePhotoUrl TEXT,
                        afterPhotoUrl TEXT,
                        emergencyAddonRequested INTEGER NOT NULL DEFAULT 0,
                        emergencyAddonAmount REAL NOT NULL DEFAULT 0.0,
                        emergencyAddonReason TEXT NOT NULL DEFAULT '',
                        emergencyAddonReceiptUrl TEXT NOT NULL DEFAULT '',
                        emergencyAddonStatus TEXT NOT NULL DEFAULT 'NONE',
                        paymentChannel TEXT NOT NULL DEFAULT 'MTN_MOMO',
                        momoPhoneNumber TEXT NOT NULL DEFAULT '+256 772 198 420',
                        escrowStatus TEXT NOT NULL DEFAULT 'QUOTE_PENDING',
                        doorstepPin TEXT NOT NULL DEFAULT '4829',
                        isDoorstepVerified INTEGER NOT NULL DEFAULT 0,
                        materialAdvanceRequested INTEGER NOT NULL DEFAULT 0,
                        materialAdvanceAmount REAL NOT NULL DEFAULT 0.0,
                        materialAdvanceReceiptUrl TEXT NOT NULL DEFAULT '',
                        materialAdvanceStatus TEXT NOT NULL DEFAULT 'NONE',
                        isSosTriggered INTEGER NOT NULL DEFAULT 0,
                        calloutFee REAL NOT NULL DEFAULT 5.0,
                        jobType TEXT NOT NULL DEFAULT 'ONE_TIME',
                        recurringSchedule TEXT NOT NULL DEFAULT '',
                        recurringStartDate TEXT NOT NULL DEFAULT '',
                        recurringContractProposal TEXT NOT NULL DEFAULT '',
                        recurringNegotiatedRate REAL NOT NULL DEFAULT 0.0,
                        recurringProposalStatus TEXT NOT NULL DEFAULT 'NONE',
                        scheduledAt INTEGER NOT NULL
                    );
CREATE TABLE IF NOT EXISTS `service_providers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `tagline` TEXT NOT NULL, `category` TEXT NOT NULL, `neighborhood` TEXT NOT NULL, `residenceYears` INTEGER NOT NULL, `hourlyRate` REAL NOT NULL, `distanceMiles` REAL NOT NULL, `rating` REAL NOT NULL, `completedJobsCount` INTEGER NOT NULL, `avgResponseTime` TEXT NOT NULL, `isVerifiedResident` INTEGER NOT NULL, `isBackgroundChecked` INTEGER NOT NULL, `bio` TEXT NOT NULL, `skillsCsv` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `endorsementsCount` INTEGER NOT NULL, `nationalIdNumber` TEXT NOT NULL, `idDocumentUrl` TEXT NOT NULL, `selfieVerificationUrl` TEXT NOT NULL, `kycStatus` TEXT NOT NULL, `isActivated` INTEGER NOT NULL, `momoNumber` TEXT NOT NULL, `momoProvider` TEXT NOT NULL, `walletBalance` REAL NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `user_profiles` (`id` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `fullName` TEXT NOT NULL, `district` TEXT NOT NULL, `initialRole` TEXT NOT NULL, `currentActiveMode` TEXT NOT NULL, `workerKycStatus` TEXT NOT NULL, `isWorkerActivated` INTEGER NOT NULL, `trainingCompleted` INTEGER NOT NULL, `nationalIdNumber` TEXT NOT NULL, `primarySkill` TEXT, `additionalSkillsCsv` TEXT NOT NULL, `avatarUrl` TEXT NOT NULL, `momoNumber` TEXT NOT NULL, `momoProvider` TEXT NOT NULL, `walletBalance` REAL NOT NULL, `latitude` REAL, `longitude` REAL, `address` TEXT, `createdAtTimestamp` INTEGER NOT NULL, `updatedAtTimestamp` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `worker_kyc` (`userId` TEXT NOT NULL, `fullLegalName` TEXT NOT NULL, `nationalIdNumber` TEXT NOT NULL, `dateOfBirth` TEXT NOT NULL, `gender` TEXT NOT NULL, `idFrontStoragePath` TEXT NOT NULL, `idBackStoragePath` TEXT NOT NULL, `status` TEXT NOT NULL, `submittedAt` INTEGER NOT NULL, `reviewedAt` INTEGER NOT NULL, `reviewFeedback` TEXT NOT NULL, `identityDocumentUri` TEXT, `identityDocumentIssuingAuthority` TEXT, `identityDocumentExpiry` INTEGER, `identityDocumentConsentGiven` INTEGER NOT NULL, `identityDocumentConsentWithdrawn` INTEGER NOT NULL, `addressDocumentUri` TEXT, `addressDocumentIssuingAuthority` TEXT, `addressDocumentExpiry` INTEGER, `addressDocumentUtilityBillDate` INTEGER, `addressDocumentConsentGiven` INTEGER NOT NULL, `addressDocumentConsentWithdrawn` INTEGER NOT NULL, `incomeDocumentUri` TEXT, `incomeDocumentType` TEXT, `incomeDocumentDate` INTEGER, `incomeDocumentConsentGiven` INTEGER NOT NULL, `incomeDocumentConsentWithdrawn` INTEGER NOT NULL, `sourceDocumentUri` TEXT, `sourceDocumentType` TEXT, `sourceDocumentAuthenticityVerified` INTEGER NOT NULL, `sourceDocumentConsentGiven` INTEGER NOT NULL, `sourceDocumentConsentWithdrawn` INTEGER NOT NULL, `faceMatchResult` REAL, `faceIdPhotoUri` TEXT, `faceMatchThreshold` REAL NOT NULL, `faceRecognitionConsentGiven` INTEGER NOT NULL, `faceRecognitionConsentWithdrawn` INTEGER NOT NULL, PRIMARY KEY(`userId`), FOREIGN KEY(`userId`) REFERENCES `user_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS `index_worker_kyc_userId` ON `worker_kyc` (`userId`);
CREATE TABLE IF NOT EXISTS `guarantors` (`id` TEXT NOT NULL, `workerKycId` TEXT NOT NULL, `fullName` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `relationship` TEXT NOT NULL, `district` TEXT NOT NULL, `idFrontStoragePath` TEXT NOT NULL, `idBackStoragePath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`workerKycId`) REFERENCES `worker_kyc`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS `index_guarantors_workerKycId` ON `guarantors` (`workerKycId`);
CREATE TABLE IF NOT EXISTS `worker_training` (`userId` TEXT NOT NULL, `status` TEXT NOT NULL, `completedModulesCsv` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `completionTimestamp` INTEGER NOT NULL, `currentModuleIndex` INTEGER NOT NULL, `totalModules` INTEGER NOT NULL, `platformRulesCompleted` INTEGER NOT NULL, `escrowGuidelinesCompleted` INTEGER NOT NULL, `customerEtiquetteCompleted` INTEGER NOT NULL, `safetyProtocolsCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`userId`), FOREIGN KEY(`userId`) REFERENCES `user_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS `index_worker_training_userId` ON `worker_training` (`userId`);
CREATE TABLE IF NOT EXISTS `job_quotes` (`id` TEXT NOT NULL, `jobId` TEXT NOT NULL, `workerId` TEXT NOT NULL, `workerName` TEXT NOT NULL, `proposedRate` REAL NOT NULL, `proposedHours` REAL NOT NULL, `totalQuote` REAL NOT NULL, `notes` TEXT NOT NULL, `status` TEXT NOT NULL, `submittedAt` INTEGER NOT NULL, `respondedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`jobId`) REFERENCES `job_posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`workerId`) REFERENCES `service_providers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS `index_job_quotes_jobId` ON `job_quotes` (`jobId`);
CREATE INDEX IF NOT EXISTS `index_job_quotes_workerId` ON `job_quotes` (`workerId`);
CREATE TABLE IF NOT EXISTS `pending_actions` (`id` TEXT NOT NULL, `actionType` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, `maxRetries` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`));