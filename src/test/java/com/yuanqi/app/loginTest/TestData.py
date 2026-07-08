'''
数据模板
{
    username:qm,
    password:123456
}
'''
import json


# 生成一个字典，里面有username和password两个字段
# 随机生成username和password
# 生成json文件，将构造的数据保存进去


def testDateGenerate():
    valid_user = "qm"
    valid_password = 123456
    test_data = []

    ##正向用例
    test_data.append(
        {
            "desc": "正向用例，正确的用户名和密码",
            "username": valid_user,
            "password": valid_password,
            "expect_status": 200
        }
    )

    # 反向用例，错误的用户名和正确的密码
    #
    fail_user = [
        {"label": "用户名超长", "val": "a" * 50, "password": valid_password, "expect_status": 400},
        {"label": "用户名超短", "val": "a", "password": valid_password, "expect_status": 400},
        {"label": "用户名不存在", "val": "sb", "password": valid_password, "expect_status": 400},
        {"label": "用户名含有特殊字符", "val": "！@#￥", "password": valid_password, "expect_status": 400},
        {"label": "用户名为空", "val": "", "password": valid_password, "expect_status": 400},
        {"label": "用户名包含空格", "val": "q m", "password": valid_password, "expect_status": 400}
    ]

    for i in fail_user:
        test_data.append({
            "desc": f"反向用例，{i['label']}",
            "username": i['val'],
            "password": i['password'],
            "expect_status": i['expect_status']
        })

    ##反向用例，正确的用户名和错误的密码
    fail_password = [
        {"label": "密码不到6位", "username": valid_user, "val": "12345", "expect_status": 400},
        {"label": "密码超长", "username": valid_user, "val": "a" * 50, "expect_status": 400},
        {"label": "密码含有特殊字符", "username": valid_user, "val": "1234！@#￥5", "expect_status": 400},
        {"label": "密码为空", "username": valid_user, "val": "", "expect_status": 400},
        {"label": "密码含有空格", "username": valid_user, "val": "123 45", "expect_status": 400}
    ]

    for i in fail_password:
        test_data.append({
            "desc": f"反向用例,{i['label']}",
            "username": i['username'],
            "password": i['val'],
            "expect_status": i['expect_status']
        })
    return test_data


if __name__ == '__main__':
    data = testDateGenerate()

    # 写入 JSON 文件
    file_name = "login_test_data.json"
    with open(file_name, "w", encoding="utf-8") as f:
        # ensure_ascii=False 保证中文正常显示，indent=4 保证JSON格式美观可读
        json.dump(data, f, ensure_ascii=False, indent=4)

    print(f"成功生成 {len(data)} 条测试数据！已保存至 {file_name}")
