# 配置设置说明

## Supabase 配置

为了保护敏感信息，项目中的 Supabase 配置需要手动设置：

### 步骤：

1. 复制示例配置文件：
   ```
   复制 app/src/main/java/com/example/xiaomaotai/SupabaseConfig.example.kt
   重命名为 SupabaseConfig.kt
   ```

2. 填入你的实际配置：
   - `SUPABASE_URL`: 你的 Supabase 项目 URL
   - `SUPABASE_ANON_KEY`: 你的 Supabase anon key

3. 确保 `SupabaseConfig.kt` 不会被提交到 Git（已在 .gitignore 中配置）

### 注意事项：
- 不要将真实的 API 密钥提交到版本控制系统
- 每个开发者需要使用自己的配置文件
- 生产环境应使用环境变量或安全的配置管理系统
