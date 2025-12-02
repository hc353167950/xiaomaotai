import os
import sys
from supabase import create_client, Client
import time

def keep_alive():
    try:
        # 从环境变量获取凭据
        url = os.environ.get("SUPABASE_URL")
        key = os.environ.get("SUPABASE_KEY")
        
        if not url or not key:
            print("❌ 错误：缺少 Supabase 凭据")
            print("请设置 SUPABASE_URL 和 SUPABASE_KEY 环境变量")
            return False
            
        print("🔗 正在连接到 Supabase...")
        supabase: Client = create_client(url, key)
        
        # ========== 1. Database 活动 (REST API) ==========
        print("\n📊 [1/4] 执行 Database 操作...")
        
        # === 1.1 查询操作 ===
        print("\n  🔍 [1.1] 执行查询操作...")
        try:
            # 查询表中的数据（限制返回1条，减少数据传输）
            result = supabase.table("keep_alive").select("*").limit(1).execute()
            
            if hasattr(result, 'data'):
                record_count = len(result.data) if result.data else 0
                print(f"  ✅ Database 查询: 成功读取 {record_count} 条记录")
                
                # 如果表中有数据，显示第一条
                if result.data and len(result.data) > 0:
                    first_record = result.data[0]
                    print(f"  📄 示例数据: {first_record}")
            else:
                print("  ⚠️ Database 查询: 未获取到数据结构")
                
        except Exception as e:
            print(f"  ❌ Database 查询失败: {str(e)}")
            # 如果表不存在，给出提示
            if "relation" in str(e).lower() and "does not exist" in str(e).lower():
                print("  ℹ️ 提示: 'keep_alive' 表可能不存在，请先创建该表")
        
        # === 1.2 插入操作 ===
        print("\n  ➕ [1.2] 执行插入操作...")
        try:
            data = {
                "name": "keep_alive", 
                "value": f"keep_alive_{int(time.time())}",
                "created_at": time.strftime("%Y-%m-%d %H:%M:%S")
            }
            result = supabase.table("keep_alive").insert(data).execute()
            
            if hasattr(result, 'data') and result.data:
                print(f"  ✅ Database 插入: 成功插入 {len(result.data)} 条数据")
                
                # === 1.3 删除操作（清理测试数据）===
                print("\n  🗑️ [1.3] 清理测试数据...")
                for item in result.data:
                    delete_result = supabase.table("keep_alive").delete().eq("id", item['id']).execute()
                    print(f"  ✅ Database 删除: 已删除 ID={item['id']} 的记录")
            else:
                print("  ⚠️ Database 插入: 未插入任何数据")
                
        except Exception as e:
            print(f"  ❌ Database 插入/删除失败: {str(e)}")
        
        # === 1.4 计数操作（额外的查询活动）===
        print("\n  🔢 [1.4] 执行计数操作...")
        try:
            # 获取表中总记录数
            result = supabase.table("keep_alive").select("*", count="exact").execute()
            if hasattr(result, 'count'):
                print(f"  ✅ Database 计数: 表中共有 {result.count} 条记录")
            else:
                print(f"  ✅ Database 计数: 查询已执行")
        except Exception as e:
            print(f"  ⚠️ Database 计数失败: {str(e)}")
        
        print("\n  ✅ Database 操作模块完成 (查询→插入→删除→计数)")
        
        # ========== 2. Auth 活动 ==========
        print("\n🔐 [2/4] 执行 Auth 操作...")
        try:
            # 方法1: 获取当前用户（即使返回 None 也会产生请求）
            user = supabase.auth.get_user()
            print(f"  ✅ Auth: 成功触发 get_user 请求")
            
        except Exception as e:
            # 即使报错，请求也已发出
            print(f"  ✅ Auth: 已触发认证请求 (这是正常的)")
        
        # 方法2: 如果需要测试登录（可选）
        test_email = os.environ.get("TEST_EMAIL")
        test_password = os.environ.get("TEST_PASSWORD")
        if test_email and test_password:
            try:
                # 正确的登录方法
                response = supabase.auth.sign_in_with_password({
                    "email": test_email,
                    "password": test_password
                })
                if response.user:
                    print("  ✅ Auth: 测试登录成功")
                    # 登出
                    supabase.auth.sign_out()
                    print("  ✅ Auth: 已登出")
            except Exception as e:
                print(f"  ⚠️ Auth: 测试登录失败 ({str(e)[:80]})")
        
        # ========== 3. Storage 活动 ==========
        print("\n📁 [3/4] 执行 Storage 操作...")
        try:
            # 列出所有 bucket
            buckets = supabase.storage.list_buckets()
            print(f"  ✅ Storage: 成功列出存储桶 (共 {len(buckets)} 个)")
            
            # 如果有 bucket，尝试列出第一个 bucket 的文件
            if buckets and len(buckets) > 0:
                first_bucket = buckets[0]
                try:
                    files = supabase.storage.from_(first_bucket.name).list()
                    print(f"  ✅ Storage: 列出 '{first_bucket.name}' 中的文件 (共 {len(files)} 个)")
                except Exception as e:
                    print(f"  ⚠️ Storage: 列出文件失败 ({str(e)[:50]})")
            
        except Exception as e:
            print(f"  ⚠️ Storage: 操作失败 ({str(e)[:80]})")
        
        # ========== 4. Realtime 活动（可选，因为可能有问题）==========
        print("\n⚡ [4/4] 执行 Realtime 操作...")
        try:
            # Python SDK 的 Realtime 支持有限，这里简化处理
            # 如果 Realtime 不工作，可以跳过这一步
            print("  ℹ️ Realtime: Python SDK 对 Realtime 支持有限，跳过此步骤")
            print("  ✅ Realtime: 前3个操作已足够保活")
            
        except Exception as e:
            print(f"  ⚠️ Realtime: 操作失败 ({str(e)[:80]})")
        
        print("\n" + "="*50)
        print("✅ 保活任务执行成功")
        print("="*50)
        return True
        
    except Exception as e:
        print(f"\n❌ 执行保活任务时出错: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    print("="*50)
    print("🚀 开始执行 Supabase 全方位保活任务")
    print("="*50)
    
    success = keep_alive()
    
    print("\n" + "="*50)
    print("📋 保活任务执行完成")
    print(f"状态: {'✅ 成功' if success else '❌ 失败'}")
    print("="*50)
    
    sys.exit(0 if success else 1)
