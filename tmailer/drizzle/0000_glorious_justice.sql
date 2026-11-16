CREATE TABLE `tmailer_auth_limits` (
	`key` text PRIMARY KEY NOT NULL,
	`count` integer NOT NULL,
	`expires_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `tmailer_devices` (
	`id` text PRIMARY KEY NOT NULL,
	`secret_hash` text NOT NULL,
	`state` text DEFAULT 'pending' NOT NULL,
	`encrypted_token` text,
	`expires_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `tmailer_sessions` (
	`token_hash` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`expires_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `tmailer_users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `tmailer_sessions_owner` ON `tmailer_sessions` (`user_id`);--> statement-breakpoint
CREATE INDEX `tmailer_sessions_expiry` ON `tmailer_sessions` (`expires_at`);--> statement-breakpoint
CREATE TABLE `tmailer_users` (
	`id` text PRIMARY KEY NOT NULL,
	`email` text,
	`login_email` text,
	`password_hash` text,
	`chatgpt_subject` text,
	`display_name` text NOT NULL,
	`settings_json` text DEFAULT '{}' NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `tmailer_users_login_email` ON `tmailer_users` (`login_email`);--> statement-breakpoint
CREATE UNIQUE INDEX `tmailer_users_chatgpt_subject` ON `tmailer_users` (`chatgpt_subject`);--> statement-breakpoint
CREATE TABLE `tmailer_vault` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`email` text NOT NULL,
	`protocol` text NOT NULL,
	`encrypted_config` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `tmailer_users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `tmailer_vault_owner` ON `tmailer_vault` (`user_id`);