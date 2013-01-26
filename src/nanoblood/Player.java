/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nanoblood;

import org.newdawn.slick.Animation;
import org.newdawn.slick.Image;
import org.newdawn.slick.Renderable;
import org.newdawn.slick.SlickException;

/**
 *
 * @author jonas
 */
public class Player extends Sprite {

    private enum Anim {

        STATIC, UP, DOWN
    }
    Anim currentAnim;

    Image staticShip;
    Animation staticA;
    Animation down;
    Animation up;

    public Player() throws SlickException {
        this.staticShip = Sprite.getImage("sprites/player/static.png");

        Image anim[] = new Image[48];
        for (int i = 0; i < anim.length; i++) {
            anim[i] = Sprite.getImage("sprites/player/staticA/mainperso1_" + Sprite.intToString(i, 5) + ".png");
        }
        this.staticA = new Animation(anim, 50, true);

        anim = new Image[20];
        for (int i = 0; i < anim.length; i++) {
            anim[i] = Sprite.getImage("sprites/obstacles/coffee/coffe" + Sprite.intToString(i, 5) + ".png");
        }
        this.down = new Animation(anim, 50, true);

        anim = new Image[20];
        for (int i = 0; i < anim.length; i++) {
            anim[i] = Sprite.getImage("sprites/obstacles/coffee/coffe" + Sprite.intToString(i, 5) + ".png");
        }
        this.up = new Animation(anim, 50, true);


        this.staticA.start();
        currentAnim = Anim.STATIC;
    }

    @Override
    public Renderable getRenderable() {
        switch (this.currentAnim) {
            case STATIC:
                return this.staticA;

            case UP:
                return this.up;

            case DOWN:
                return this.down;
        }

        return this.staticShip;
    }

    public void goUp() {
        this.coords.setLocation(this.coords.getX(), this.coords.getY() - 5);
        this.up.start();
        this.down.stop();
        this.staticA.stop();

        currentAnim = Anim.UP;
    }

    public void goDown() {
        this.coords.setLocation(this.coords.getX(), this.coords.getY() + 5);
        this.down.start();
        this.up.stop();
        this.staticA.stop();

        currentAnim = Anim.DOWN;
    }

    public void stop() {
        this.staticA.start();
        this.down.stop();
        this.up.stop();

        currentAnim = Anim.STATIC;
    }
}
