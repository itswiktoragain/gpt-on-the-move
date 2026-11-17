CREATE TABLE `tmailer_mail_sessions` (
	`token_hash` text PRIMARY KEY NOT NULL,
	`user_id` text,
	`encrypted_config` text,
	`expires_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `tmailer_users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `tmailer_mail_sessions_expiry` ON `tmailer_mail_sessions` (`expires_at`);