import os
import sys
from supabase import create_client, Client
import time

def keep_alive():
    try:
        url = os.environ.get("SUPABASE_URL")
        key = os.environ.get("SUPABASE_KEY")
        
        if not url or not key:
            print("❌ 错误：缺少 Supabase 凭据")
            return False
            
        print("🔗 正在连接到 Supabase...")
        supabase: Client = create_client(url, key)
        
        # ========== 1. Database 活动 ==========
        print("\n📊 [1/4] 执行 Database 操作...")
        try:
            # 多种操作确保触发请求
            print("  🔍 [1.1] 执行查询操作...")
            result = supabase.table("keep_alive").select("*").limit(1).execute()
            print(f"  ✅ Database 查询: 成功读取 {len(result.data)} 条记录")
            
            print("  ➕ [1.2] 执行插入操作...")
            data = {"name": "keep_alive", "value": f"keep_alive_{int(time.time())}"}
            insert_result = supabase.table("keep_alive").insert(data).execute()
            print(f"  ✅ Database 插入: 成功插入 {len(insert_result.data)} 条数据")
            
            if insert_result.data:
                print("  🗑️ [1.3] 清理测试数据...")
                for item in insert_result.data:
                    supabase.table("keep_alive").delete().eq("id", item['id']).execute()
                    print(f"  ✅ Database 删除: 已删除 ID={item['id']} 的记录")
            
            print("  ✅ Database 操作模块完成")
        except Exception as e:
            print(f"  ❌ Database 操作失败: {str(e)}")
        
        # ========== 2. Auth 活动（改进版）==========
        print("\n🔐 [2/4] 执行 Auth 操作...")
        auth_success = False
        
        # 方法1: 尝试匿名登录（推荐）
        print("  📝 [2.1] 尝试匿名登录...")
        try:
            # 先登出（如果有会话）
            try:
                supabase.auth.sign_out()
            except:
                pass
            
            # 匿名登录
            response = supabase.auth.sign_in_anonymously()
            if response and response.user:
                print(f"  ✅ Auth 匿名登录: 成功 (用户ID: {response.user.id[:8]}...)")
                auth_success = True
                # 立即登出
                supabase.auth.sign_out()
                print("  ✅ Auth: 已登出")
            else:
                print("  ⚠️ Auth 匿名登录: 响应异常")
        except Exception as e:
            print(f"  ⚠️ Auth 匿名登录失败: {str(e)[:100]}")
        
        # 方法2: 尝试使用测试邮箱登录（如果配置了）
        if not auth_success:
            print("  📧 [2.2] 尝试邮箱登录...")
            test_email = os.environ.get("TEST_EMAIL")
            test_password = os.environ.get("TEST_PASSWORD")
            
            if test_email and test_password:
                try:
                    response = supabase.auth.sign_in_with_password({
                        "email": test_email,
                        "password": test_password
                    })
                    if response and response.user:
                        print(f"  ✅ Auth 邮箱登录: 成功")
                        auth_success = True
                        supabase.auth.sign_out()
                        print("  ✅ Auth: 已登出")
                except Exception as e:
                    print(f"  ⚠️ Auth 邮箱登录失败: {str(e)[:100]}")
            else:
                print("  ℹ️ 未配置测试邮箱凭据")
        
        # 方法3: 触发密码重置请求（即使邮箱不存在也会产生请求）
        if not auth_success:
            print("  🔄 [2.3] 触发密码重置请求...")
            try:
                fake_email = f"keep_alive_{int(time.time())}@example.com"
                supabase.auth.reset_password_email(fake_email)
                print(f"  ✅ Auth 密码重置: 已触发请求")
                auth_success = True
            except Exception as e:
                # 即使失败也计入请求
                print(f"  ✅ Auth 密码重置: 已触发请求 (预期错误: {str(e)[:50]})")
                auth_success = True
        
        if auth_success:
            print("  ✅ Auth 操作模块完成")
        else:
            print("  ⚠️ Auth 操作可能未生效")
        
        # ========== 3. Storage 活动 ==========
        print("\n📁 [3/4] 执行 Storage 操作...")
        try:
            buckets = supabase.storage.list_buckets()
            print(f"  ✅ Storage 列出桶: 成功 (共 {len(buckets)} 个)")
            
            # 如果没有 bucket，尝试列出文件也会产生请求
            if len(buckets) == 0:
                print("  ℹ️ 无存储桶，尝试触发其他 Storage 请求...")
                try:
                    # 尝试访问一个不存在的 bucket 也会计入请求
                    supabase.storage.from_('keep_alive_bucket').list()
                except:
                    print("  ✅ Storage: 已触发额外请求")
            
            print("  ✅ Storage 操作模块完成")
        except Exception as e:
            print(f"  ✅ Storage: 已触发请求 (错误: {str(e)[:50]})")
        
        # ========== 4. Realtime 活动 ==========
        print("\n⚡ [4/4] 执行 Realtime 操作...")
        try:
            # 订阅一个表的变化
            channel_name = f"keep_alive_{int(time.time())}"
            channel = supabase.channel(channel_name)
            
            # 订阅表变化
            channel.on_postgres_changes(
                event='*',
                schema='public',
                table='keep_alive',
                callback=lambda payload: print(f"  📡 收到 Realtime 事件: {payload}")
            ).subscribe()
            
            print(f"  ✅ Realtime 订阅: 成功订阅频道 '{channel_name}'")
            time.sleep(3)  # 保持连接3秒
            
            channel.unsubscribe()
            print("  ✅ Realtime 取消订阅: 已断开连接")
            print("  ✅ Realtime 操作模块完成")
        except Exception as e:
            print(f"  ⚠️ Realtime: {str(e)[:100]}")
            print("  ℹ️ Realtime 在某些环境下可能不支持，但前3项已足够保活")
        
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
