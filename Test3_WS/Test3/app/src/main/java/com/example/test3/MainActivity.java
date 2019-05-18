package com.example.test3;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    Intent phoneIntent;
    EditText phonenumberView;
    Button startbutton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startbutton = (Button) findViewById(R.id.button);

        phonenumberView = (EditText) findViewById(R.id.editText1);
        phoneIntent = new Intent(this,DetectingActivity.class);


        startbutton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v){
                //왠지 모르게 getText().toString을 해줘야 값이 전달이 된다.
                phoneIntent.putExtra("number",phonenumberView.getText().toString());
                startActivity(phoneIntent);
            }
        });



    }
}
