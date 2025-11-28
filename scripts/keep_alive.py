import os
import sys
from supabase import create_client, Client

def keep_alive():
    try:
        # 从环境变量获取凭据
        url = os.environ.get("SUPABASE_URL")
        key = os.environ.get("SUPABASE_KEY")
        
        if not url or not key:
            print("错误：缺少 Supabase 凭据")
            print("请设置 SUPABASE_URL 和 SUPABASE_KEY 环境变量")
            return False
            
        print("正在连接到 Supabase...")
        supabase: Client = create_client(url, key)
        
        # 插入测试数据
        print("正在插入测试数据...")
        data = {"name": "keep_alive", "value": "keep_alive"}
        result = supabase.table("keep_alive").insert(data).execute()
        
        if hasattr(result, 'data') and result.data:
            print(f"成功插入数据: {result.data}")
            
            # 删除刚插入的数据
            # print("正在清理测试数据...")
            # for item in result.data:
            #     delete_result = supabase.table("keep_alive").delete().eq("id", item['id']).execute()
            #     print(f"已删除ID为 {item['id']} 的记录")
        else:
            print("警告：未插入任何数据")
            
        print("✅ 保活任务执行成功")
        return True
        
    except Exception as e:
        print(f"❌ 执行保活任务时出错: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    print("="*50)
    print("开始执行 Supabase 保活任务")
    print("="*50)
    
    success = keep_alive()
    
    print("="*50)
    print("保活任务执行完成")
    print(f"状态: {'✅ 成功' if success else '❌ 失败'}")
    print("="*50)
    

    sys.exit(0 if success else 1)
