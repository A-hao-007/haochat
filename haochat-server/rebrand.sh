#!/bin/bash
set -e
cd /root/HaoChat

echo "=== 1. 替换 Java 包名 ==="
find . -type f \( -name "*.java" -o -name "*.xml" -o -name "*.properties" -o -name "*.yml" -o -name "*.factories" \) \
  -exec sed -i 's/com\.abin\.mallchat/com.ahao.haochat/g' {} +
echo "Done: com.abin.mallchat → com.ahao.haochat"

echo "=== 2. 重命名 POM artifact ==="
find . -name "pom.xml" -exec sed -i 's/<artifactId>mallchat-/<artifactId>haochat-/g' {} +
find . -name "pom.xml" -exec sed -i 's/<artifactId>mallchat<\/artifactId>/<artifactId>haochat<\/artifactId>/g' {} +
find . -name "pom.xml" -exec sed -i 's/<module>mallchat-/<module>haochat-/g' {} +
echo "Done: POM artifacts"

echo "=== 3. 重命名主类 ==="
find . -name "MallchatCustomApplication.java" -exec sh -c 'mv "$1" "$(dirname "$1")/HaochatCustomApplication.java"' _ {} \;
find . -type f \( -name "*.java" -o -name "*.xml" -o -name "*.properties" -o -name "*.yml" \) \
  -exec sed -i 's/MallchatCustomApplication/HaochatCustomApplication/g' {} +
echo "Done: Main class"

echo "=== 4. 替换配置文件 ==="
find . -name "application.yml" -exec sed -i 's/name: mallchat/name: haochat/g' {} +
find . -type f \( -name "*.properties" -o -name "*.yml" \) \
  -exec sed -i 's/mallchat\./haochat./g' {} +
# Fix double prefix replacement
find . -type f \( -name "*.properties" -o -name "*.yml" \) \
  -exec sed -i 's/haochat\.mysql\.db=mallchat/haochat.mysql.db=haochat/g' {} +
echo "Done: Config prefixes"

echo "=== 5. 替换 Swagger 配置 ==="
find . -name "SwaggerConfig.java" -exec sed -i 's/mallchat接口文档/haoChat接口文档/g' {} +
find . -name "SwaggerConfig.java" -exec sed -i 's/阿斌/A-hao/g' {} +
find . -name "SwaggerConfig.java" -exec sed -i 's|http://www.mallchat.cn|https://ahao.space|g' {} +
find . -name "SwaggerConfig.java" -exec sed -i 's/972627721@qq.com/1343266003@qq.com/g' {} +
echo "Done: Swagger"

echo "=== 6. 替换 author 标签 ==="
find . -type f -name "*.java" \
  -exec sed -i 's|https://github.com/zongzibinbin|https://github.com/A-hao-007|g' {} +
find . -type f -name "*.java" \
  -exec sed -i 's|zongzibinbin|A-hao-007|g' {} +
find . -type f -name "*.java" \
  -exec sed -i 's/@author.*abin/@author A-hao/g' {} +
echo "Done: Author tags"

echo "=== 7. 替换域名和外部链接 ==="
find . -type f \( -name "*.yml" -o -name "*.properties" -o -name "*.java" -o -name "*.md" \) \
  -exec sed -i 's|mallchat\.cn|ahao.space|g' {} +
find . -type f \( -name "*.yml" -o -name "*.properties" -o -name "*.java" -o -name "*.md" \) \
  -exec sed -i 's|limeng-test\.nat300\.top|ahao.space|g' {} +
echo "Done: Domains"

echo "=== 8. 替换 B站链接 ==="
find . -type f \( -name "*.md" -o -name "*.vue" -o -name "*.ts" -o -name "*.js" \) \
  -exec sed -i 's|space\.bilibili\.com/146719540|space.bilibili.com/476740153|g' {} +
echo "Done: Bilibili"

echo "=== 9. 替换 GitHub 仓库名 ==="
find . -type f \( -name "*.md" -o -name "*.yml" \) \
  -exec sed -i 's|zongzibinbin/MallChat|A-hao-007/HaoChat|g' {} +
find . -type f \( -name "*.md" -o -name "*.yml" \) \
  -exec sed -i 's|Evansy/MallChatWeb|A-hao-007/HaoChatWeb|g' {} +
echo "Done: GitHub repos"

echo "=== 10. 替换邮箱 ==="
find . -type f \( -name "*.java" -o -name "*.md" \) \
  -exec sed -i 's/972627721@qq.com/1343266003@qq.com/g' {} +
echo "Done: Email"

echo "=== 11. 替换 SQL 中的品牌名 ==="
find docs -name "*.sql" -exec sed -i 's/抹茶聊天/好聊/g' {} +
find docs -name "*.sql" -exec sed -i 's/抹茶项目/好聊项目/g' {} +
find docs -name "*.sql" -exec sed -i 's/抹茶知识星球/好聊社区/g' {} +
find docs -name "*.sql" -exec sed -i 's/抹茶群聊管理员/好聊群管理员/g' {} +
find docs -name "*.sql" -exec sed -i 's/抹茶全员群/好聊全员群/g' {} +
find docs -name "*.sql" -exec sed -i 's|https://minio\.mallchat\.cn/mallchat/|https://minio.ahao.space/haochat/|g' {} +
echo "Done: SQL branding"

echo "=== 12. 替换文档中的中文名 ==="
find . -name "*.md" -exec sed -i 's/MallChat-抹茶/HaoChat-好聊/g' {} +
find . -name "*.md" -exec sed -i 's/抹茶聊天/好聊/g' {} +
find . -name "*.md" -exec sed -i 's/程序员阿斌/A-hao/g' {} +
find . -name "*.md" -exec sed -i 's/阿斌Java之路/A-hao技术分享/g' {} +
echo "Done: README branding"

echo "=== 验证 ==="
echo "剩余旧包名:"
grep -r "com\.abin\.mallchat" --include="*.java" --include="*.xml" --include="*.properties" . 2>/dev/null | wc -l
echo "剩余old域名:"
grep -r "mallchat\.cn" --include="*.java" --include="*.md" . 2>/dev/null | wc -l

echo ""
echo "=== 全部完成 ==="
