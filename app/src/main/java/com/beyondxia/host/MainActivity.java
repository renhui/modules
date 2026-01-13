package com.beyondxia.host;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.beyondxia.bussiness1.export.ILogin;
import com.beyondxia.bussiness1.export.LoginService;
import com.beyondxia.message.export.IMessage;
import com.beyondxia.message.export.MessageService;
import com.beyondxia.modules.BCDictionary;
import com.beyondxia.modules.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("HostApp");

    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bt_business1_call:
                ILogin loginService = LoginService.get();
                if (loginService != null) {
                    String username = loginService.getUserName();
                    Toast.makeText(this, username, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "LoginService未初始化，请检查服务注册", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_business1_call2:
                ILogin loginService2 = LoginService.get();
                if (loginService2 != null) {
                    boolean success = loginService2.doLogin(this, "chenwei", "xiaxiaojun");
                    Toast.makeText(this, success ? "login success" : "login failed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "LoginService未初始化，请检查服务注册", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_business1_call3:
                ILogin loginService3 = LoginService.get();
                if (loginService3 != null) {
                    BCDictionary dictionary = loginService3.getUserInfo();
                    Toast.makeText(this, dictionary.toJson(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "LoginService未初始化，请检查服务注册", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_business1_nav:
                ILogin loginService4 = LoginService.get();
                if (loginService4 != null) {
                    loginService4.nav2LoginActivity(this);
                } else {
                    Toast.makeText(this, "LoginService未初始化，请检查服务注册", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_business2_nav:
                IMessage messageService = MessageService.get();
                if (messageService != null) {
                    messageService.nav2B2Activity(this);
                } else {
                    Toast.makeText(this, "MessageService未初始化，请检查服务注册", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_submodule_view:
                startActivity(new Intent(this, ShowViewActivity.class));
                break;
            case R.id.bt_submodule_fragment:
                startActivity(new Intent(this, ShowFragmentActivity.class));
                break;
            default:
                break;
        }
    }
}
