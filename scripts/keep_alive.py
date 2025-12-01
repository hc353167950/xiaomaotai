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
        try:
            data = {"name": "keep_alive", "value": f"keep_alive_{int(time.time())}"}
            result = supabase.table("keep_alive").insert(data).execute()
            
            if hasattr(result, 'data') and result.data:
                print(f"✅ Database: 成功插入数据")
                # 删除刚插入的数据
                for item in result.data:
                    supabase.table("keep_alive").delete().eq("id", item['id']).execute()
                print(f"✅ Database: 已清理测试数据")
            else:
                print("⚠️ Database: 未插入任何数据")
        except Exception as e:
            print(f"❌ Database 操作失败: {str(e)}")
        
        # ========== 2. Auth 活动 ==========
        print("\n🔐 [2/4] 执行 Auth 操作...")
        try:
            # # 方法1: 尝试获取当前会话（即使失败也会产生 Auth Request）
            # session = supabase.auth.get_session()
            # print(f"✅ Auth: 成功触发认证请求")
            
            方法2: 如果您有测试账号，可以尝试登录（可选）
            test_email = os.environ.get("TEST_EMAIL")
            test_password = os.environ.get("TEST_PASSWORD")
            if test_email and test_password:
                try:
                    supabase.auth.sign_in_with_password({
                        "email": test_email, 
                        "password": test_password
                    })
                    print("✅ Auth: 测试登录成功")
                    supabase.auth.sign_out()
                except:
                    print("⚠️ Auth: 测试登录失败（这是正常的）")
                
        except Exception as e:
            # Auth 请求即使失败也会被计入统计
            print(f"✅ Auth: 已触发认证请求 (错误被忽略: {str(e)[:50]})")
        
        # ========== 3. Storage 活动 ==========
        print("\n📁 [3/4] 执行 Storage 操作...")
        try:
            # 方法1: 列出 bucket（即使为空也会产生请求）
            buckets = supabase.storage.list_buckets()
            print(f"✅ Storage: 成功列出存储桶 (共 {len(buckets)} 个)")
            
            # 方法2: 如果有公开 bucket，可以尝试上传/删除小文件（可选）
            # bucket_name = "public"  # 替换为您的 bucket 名称
            # try:
            #     test_file = b"keep_alive"
            #     file_path = f"keep_alive_{int(time.time())}.txt"
            #     supabase.storage.from_(bucket_name).upload(file_path, test_file)
            #     print(f"✅ Storage: 成功上传测试文件")
            #     supabase.storage.from_(bucket_name).remove([file_path])
            #     print(f"✅ Storage: 已删除测试文件")
            # except Exception as e:
            #     print(f"⚠️ Storage: 文件操作失败 ({str(e)[:50]})")
                
        except Exception as e:
            print(f"✅ Storage: 已触发存储请求 (错误被忽略: {str(e)[:50]})")
        
        # ========== 4. Realtime 活动 ==========
        print("\n⚡ [4/4] 执行 Realtime 操作...")
        try:
            # 订阅一个 channel 然后立即取消订阅
            channel = supabase.channel('keep_alive_channel')
            channel.subscribe()
            print("✅ Realtime: 成功订阅频道")
            time.sleep(2)  # 保持连接2秒
            channel.unsubscribe()
            print("✅ Realtime: 已取消订阅")
        except Exception as e:
            print(f"⚠️ Realtime: 操作失败 ({str(e)[:50]})")
        
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
