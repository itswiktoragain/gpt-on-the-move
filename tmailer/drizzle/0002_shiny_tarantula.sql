CREATE TABLE `tmailer_recipients` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`encrypted_history` text NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `tmailer_users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `tmailer_recipients_owner` ON `tmailer_recipients` (`user_id`);