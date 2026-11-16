import { sqliteTable, text, integer, index, uniqueIndex } from 'drizzle-orm/sqlite-core';
export const users=sqliteTable('tmailer_users',{
 id:text('id').primaryKey(),email:text('email'),loginEmail:text('login_email'),passwordHash:text('password_hash'),chatgptSubject:text('chatgpt_subject'),displayName:text('display_name').notNull(),settings:text('settings_json').notNull().default('{}'),createdAt:integer('created_at').notNull()
},t=>[uniqueIndex('tmailer_users_login_email').on(t.loginEmail),uniqueIndex('tmailer_users_chatgpt_subject').on(t.chatgptSubject)]);
export const sessions=sqliteTable('tmailer_sessions',{
 tokenHash:text('token_hash').primaryKey(),userId:text('user_id').notNull().references(()=>users.id,{onDelete:'cascade'}),expiresAt:integer('expires_at').notNull(),createdAt:integer('created_at').notNull()
},t=>[index('tmailer_sessions_owner').on(t.userId),index('tmailer_sessions_expiry').on(t.expiresAt)]);
export const vault=sqliteTable('tmailer_vault',{
 id:text('id').primaryKey(),userId:text('user_id').notNull().references(()=>users.id,{onDelete:'cascade'}),email:text('email').notNull(),protocol:text('protocol').notNull(),encryptedConfig:text('encrypted_config').notNull(),createdAt:integer('created_at').notNull(),updatedAt:integer('updated_at').notNull()
},t=>[index('tmailer_vault_owner').on(t.userId)]);
export const authLimits=sqliteTable('tmailer_auth_limits',{
 key:text('key').primaryKey(),count:integer('count').notNull(),expiresAt:integer('expires_at').notNull()
});
export const devices=sqliteTable('tmailer_devices',{
 id:text('id').primaryKey(),secretHash:text('secret_hash').notNull(),state:text('state').notNull().default('pending'),encryptedToken:text('encrypted_token'),expiresAt:integer('expires_at').notNull()
});
