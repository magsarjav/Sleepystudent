using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using TMPro;
 
public class scri : MonoBehaviour {
    public TextMeshProUGUI Text_Status;
    public int crazy = 0;

    public int sleeping_energy = 0; 
	//public Text timerText;
	// Use this for initialization
	void Start () {
        Text_Status.text = "Normal";
	}

    // Update is called once per frame
    void Update()
    {

        float angle = transform.eulerAngles.z;
        //timerText.text = angle.ToString (); 
        // z for left right
        // Y is y itself
        Face_angle();

        Check_Sleeping();
        
    }

    void Check_Sleeping()
    {

        if (sleeping_energy > 500)
        {

            Text_Status.text = "Sleep";
            OneGUI();
        }
    }

    void OneGUI()
    {
        //if(GUI.Button(new Rect(0,10,100,32), "Vibrate!"))
        {
            Handheld.Vibrate();
        }
    }

    void Face_angle()
    {
        if (10 < transform.eulerAngles.z && transform.eulerAngles.z < 100)
        {
            //  if( transform.eulerAngles.z > 100){
            //right 

            Debug.Log("Right");
            Text_Status.text = "R";
            sleeping_energy++;



            // x back
            /* Game
            GameObject go = GameObject.Find("squirrel@stand");
            move_game2 other = (move_game2)go.GetComponent(typeof(move_game2));
            other.rotate_left();
            */

        }
        else if (300 < transform.eulerAngles.z && transform.eulerAngles.z < 350)
        {
            //if( transform.eulerAngles.z < 80){// unity
            //left
            Debug.Log("Left");
            Text_Status.text = "L";
            sleeping_energy++;




            //x front
            /* Game
            GameObject go = GameObject.Find("squirrel@stand");
            move_game2 other = (move_game2)go.GetComponent(typeof(move_game2));
            other.rotate_right();
            */


        }
        else if (10 < transform.eulerAngles.x && transform.eulerAngles.x < 100)
        {
            //  if( transform.eulerAngles.z > 100){
            //right 
            Debug.Log("Right");
            Text_Status.text = "B";
            sleeping_energy++;




            // x back
            /* Game
            GameObject go = GameObject.Find("squirrel@stand");
            move_game2 other = (move_game2)go.GetComponent(typeof(move_game2));
            other.rotate_left();
            */



        }
        else if (300 < transform.eulerAngles.x && transform.eulerAngles.x < 340)
        {
            //if( transform.eulerAngles.z < 80){// unity
            //left
            Debug.Log("Left");
            Text_Status.text = "f";
            sleeping_energy++;
            



            //x front
            /* Game
            GameObject go = GameObject.Find("squirrel@stand");
            move_game2 other = (move_game2)go.GetComponent(typeof(move_game2));
            other.rotate_right();
            */


        }
        else
        {

            sleeping_energy = 0; 
            Text_Status.text = "Normal";



        }

    }



}
