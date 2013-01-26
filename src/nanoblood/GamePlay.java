/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nanoblood;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;
import nanoblood.ui.HeartBeatDisplay;
import nanoblood.ui.LifeDisplay;
import nanoblood.ui.ScoreDisplay;
import nanoblood.util.GameParams;
import nanoblood.util.IObservable;
import nanoblood.util.IObserver;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Input;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.geom.Vector2f;
import org.newdawn.slick.state.BasicGameState;
import org.newdawn.slick.state.StateBasedGame;

/**
 *
 * @author jonas
 */
public class GamePlay extends BasicGameState implements IObservable {

    class Pair<T1, T2> {

        public T1 first;
        public T2 second;
    }
    int stateID = -1;
    Player player;
    LevelManager levelManager;
    List<StaticObject> objects;
    
    // Déclarer ses valeurs dans un properties
    float bloodSpeed = 0;
    final int bloodSpeedImpulse = 3;
    final double bloodSpeedDecrease = 0.01;
    static public final int IMPULSE_COEFF_SLOW = 13;
    static public final int IMPULSE_COEFF_MEDIUM = 16;
    static public final int IMPULSE_COEFF_HARD = 20;
    static public final int IMPULSE_COEFF_CRAZY = 30;
    private Vec2 gravity;
//	private BodyDef gndBodydef;
//	private Body gndBody;
//	private PolygonShape gndBox;
    private BodyDef playerBodyDef;
    private Body playerBody;
    private PolygonShape playerShape;
    private FixtureDef playerFD;
    private float timeStep;
    private int velocityIterations;
    private int positionIterations;
    private World world;
    int totalDistance = 0;
    int nextDistancePopObstacle;
    int deltaDistancePopObstacle = 200;

    private Vec2 speedImpulse;
    private int currentHeartBeat = INITIAL_HEARTBEATS; // Current heart beats rhythm @TODO compute its average
    //* Note : Those values are heartbeat rhythms...
    static public final int INITIAL_HEARTBEATS = 50;// per minutes
    private int HEARTBEAT_THRESHOLD_CRAZY = 150;// dying soon
    private int HEARTBEAT_THRESHOLD_MEDIUM = 90;// quite excited
    private int HEARTBEAT_THRESHOLD_HARD = 120;// runner
    private int heartBeatsSinceLastUpdate = 0;
    protected static final float PIXELS_TO_METERS_RATIO = 10.0f;
    static final int OBSTACLE_SPAWN_DELAY = 300; // delay in pixels
    private static boolean DBG = true;
    private LinkedList<Long> HBList = new LinkedList<Long>();
    
    long lastTick;
    long elapsedTime;

    // UI elements
    private int score; // score board value
    private float life; // life gauge
    ScoreDisplay scoreDisplay;
    LifeDisplay lifeDisplay;
    HeartBeatDisplay heartBeatDisplay;
    
    // Observable vars
    private boolean hasChanged;
    private ArrayList<IObserver> observers;
    
    GamePlay(int stateID) {
        this.stateID = stateID;
    }

    @Override
    public int getID() {
        return stateID;
    }

    @Override
    public void init(GameContainer gc, StateBasedGame sbg) throws SlickException {
        initPhysics();
        this.player = new Player(playerBody);
        this.levelManager = new LevelManager();
        this.objects = new ArrayList<StaticObject>();
        
        this.hasChanged = false;
        this.observers = new ArrayList<IObserver>();
        
        score = 0;
        life = 150;  // TODO
        
        // Create UI elements
        this.scoreDisplay = new ScoreDisplay();
        this.lifeDisplay = new LifeDisplay();
        this.heartBeatDisplay = new HeartBeatDisplay();
        
        // Add observers
        this.observers.add(scoreDisplay);
        this.observers.add(lifeDisplay);
        this.observers.add(heartBeatDisplay);
        
        // Notify for 1st time
        this.setChanged();
        this.notifyObservers();
        
        lastTick = System.currentTimeMillis();
        elapsedTime = 0;

        nextDistancePopObstacle = Main.width + deltaDistancePopObstacle;

        player.setCoords(200, Main.height / 2);

        for (int i = 0; i < 6; i++) {
            Obstacle o = Obstacle.getRandomObstacle();
            o.setCoords(i * 200, (int) (Math.random() * Main.height));
            this.objects.add(o);
        }
    }

    @Override
    public void enter(GameContainer container, StateBasedGame game) throws SlickException {
        System.out.println("ENTERING !");
        //* Pushing data to the HBQueue at the beginning so that the average on the last 10 seconds is not null or very low but is 70 HB
        java.util.Date date = new java.util.Date();
        for (int i = 0; i < ((double) INITIAL_HEARTBEATS / 60.0) * heartBeatAvgInterval + 1; i++) {
            HBList.add(date.getTime());// Note: The time is not necessarily different for each HB
        }
    }

    @Override
    public void render(GameContainer gc, StateBasedGame sbg, Graphics grphcs) throws SlickException {
        this.levelManager.render(gc, sbg, grphcs);

        for (StaticObject so : this.objects) {
            so.getRenderable().draw((float) so.coords.getX(), (float) so.coords.getY());
        }

        this.player.getRenderable().draw((float) this.player.getCoords().getX(), (float) this.player.getCoords().getY());
        this.player.getCanons().draw((float) this.player.getCoords().getX(), (float) this.player.getCoords().getY() - 4);
        
        // UI : render last
        this.scoreDisplay.render(gc, sbg, grphcs);
        this.lifeDisplay.render(gc, sbg, grphcs);
        this.heartBeatDisplay.render(gc, sbg, grphcs);
    }

    @Override
    public void update(GameContainer gc, StateBasedGame sbg, int delta) throws SlickException {
        
        removeObjects();
        addObjects();

        manageInput(gc, sbg, delta);

        updateCurrentHB(delta);
        updatePhysics();
        updateObjects();

        this.levelManager.update(m2px(this.playerBody.getLinearVelocity().x));

        manageColisions();
        
        // Update score very second
        long currentTime = System.currentTimeMillis();
        elapsedTime = currentTime - lastTick;
        if (elapsedTime >= 1000) {
            elapsedTime -= 1000;
            lastTick = currentTime;
            
            float scoreModifier = getScoreModifier();
            System.out.println("HB: " +currentHeartBeat + ", Modifier: " + scoreModifier);
            addScore((int) (GameParams.INSTANCE.ScorePerSecond() * scoreModifier));
        }
        
        
        // TODO game over screen
        if (life <= 0) {
            
        }
    }

    private float computeImpulseFromHeartBeat(int hb) {
        int coeff;
        if (currentHeartBeat > HEARTBEAT_THRESHOLD_CRAZY) {
            coeff = IMPULSE_COEFF_CRAZY;
        } else if (currentHeartBeat > HEARTBEAT_THRESHOLD_HARD) {
            coeff = IMPULSE_COEFF_CRAZY;
        } else if (currentHeartBeat > HEARTBEAT_THRESHOLD_MEDIUM) {
            coeff = IMPULSE_COEFF_CRAZY;
        } else {
            coeff = IMPULSE_COEFF_SLOW;
        }

        float result = coeff * currentHeartBeat;

        return result;
    }

    private void manageInput(GameContainer gc, StateBasedGame sbg, int delta) {
        Input input = gc.getInput();

        if (input.isKeyDown(Input.KEY_UP)) {
            player.goUp();
        } else if (input.isKeyDown(Input.KEY_DOWN)) {
            player.goDown();
        } else {
            player.stop();
        }

        if (input.isKeyPressed(Input.KEY_SPACE)) { // HEARTBEAT
            playerHeartBeat(delta);

        }
        
        // Update HB display
        this.setChanged();
        notifyObserver(heartBeatDisplay);
        
        // DEBUG score
        if (DBG && input.isKeyPressed(Input.KEY_TAB)) {
            score += 100;
            setChanged();
            notifyObserver(scoreDisplay);
        }
        
        // DEBUG life
        if (DBG && input.isKeyPressed(Input.KEY_A)) {
            if (life > 0) {
                life -= 10;
            }
            setChanged();
            notifyObserver(lifeDisplay);
        }

        totalDistance += bloodSpeed;//@TODO change that
    }

    private void updateObjects() {
        List<StaticObject> toRemove = new ArrayList<StaticObject>();

        for (StaticObject so : this.objects) {
            so.move((int) (-1.0f * this.playerBody.getLinearVelocity().x / 3.0f), 0);

            if (so instanceof Cancer) {
                float deltaX = (float) (m2px(playerBody.getPosition().x) - so.coords.getX());
                float deltaY = (float) (ySlick2Physics(m2px(playerBody.getPosition().y)) - so.coords.getY());

                Vector2f v = new Vector2f(deltaX, deltaY);
                v = v.normalise();

                so.move((Cancer.MOVEMENT_TO_PLAYER * v.x), (Cancer.MOVEMENT_TO_PLAYER * v.y));

//                            System.out.println(so.coords + "    " + playerBody.getPosition());
            }

            if (so.coords.getX() < -50) {
                toRemove.add(so);
            }
        }

        for (StaticObject so : toRemove) {
            this.objects.remove(so);
        }
    }

    private void manageColisions() {

        for (StaticObject so : this.objects) {
            if (this.player.boundingBox.intersects(so.getBoundingBox())) {
                so.colideWithPlayer();
                
                // TODO update life value
            }
        }
    }

    private void removeObjects() {
        for (int i = 0; i < this.objects.size();) {
            if (this.objects.get(i).needToRemove()) {
                this.objects.remove(i);
            } else {
                i++;
            }
        }
    }

    private void addObjects() throws SlickException {
        if (totalDistance > nextDistancePopObstacle) {
            Obstacle o = Obstacle.getRandomObstacle();
            o.setCoords(Main.width + OBSTACLE_SPAWN_DELAY - (totalDistance - nextDistancePopObstacle), (int) (Math.random() * Main.height));
            this.objects.add(o);
            nextDistancePopObstacle += deltaDistancePopObstacle;
        }
    }

    private void initPhysics() {
        gravity = new Vec2(0.0f, 0);
        world = new World(gravity, true);
//		gndBodydef = new BodyDef();
//		gndBodydef.position.set(0.0f, (float) (0.2 * Main.width));
//		gndBody = world.createBody(gndBodydef);
//		gndBox = new PolygonShape();
//		gndBox.setAsBox(10.0f, (float) Main.width);
//		gndBody.createFixture(gndBox, 0.0f);
        playerBodyDef = new BodyDef();
        playerBodyDef.type = BodyType.DYNAMIC;
        playerBodyDef.position.x = px2m(Main.width / 2);
        playerBodyDef.position.y = px2m((int) ySlick2Physics(Player.INIT_Y));
        playerBody = world.createBody(playerBodyDef);
        playerShape = new PolygonShape();
        playerShape.setAsBox(Player.WIDTH / 2, Player.HEIGHT / 2);
        playerFD = new FixtureDef();
        playerFD.shape = playerShape;
        playerFD.density = 1.0f;
        //* The two next lines together allow us to have friction against a number of circles even if we are a circle ourself
        playerFD.friction = 1.5f;
        playerBody.setAngularDamping(200);
        playerBody.setLinearDamping(1.2f);
        playerBody.createFixture(playerFD);
        timeStep = 1.0f / 60.0f;
        velocityIterations = 6;
        positionIterations = 2;
    }

    public float ySlick2Physics(float y) {
        return Main.height - y;
    }

    public static float yFromPhysicsToSlick(float y) {
        return Main.height - y;
    }

    private void updatePhysics() {
        world.step(timeStep, velocityIterations, positionIterations);
    }

    /**
     * Pixels to Meters converter
     * @param px
     * @return
     */
    public static float px2m(int px) {
        return px / PIXELS_TO_METERS_RATIO;
    }

    /**
     * Meters to pixels converter
     * @param m
     * @return
     */
    public static int m2px(float m) {
        return (int) (m * PIXELS_TO_METERS_RATIO);
    }
    private int heartBeatAvgInterval = 10; // In seconds

    private void updateCurrentHB(int delta) {
        // Computing average:
        Long tenSecondsAgo = new Date().getTime() - heartBeatAvgInterval * 1000;
        int sum = 0; // sum of the HB in the last 10 seconds
        for (int i = 0; i < HBList.size();) {// until the end of the list (/!\ the list is changed inside the loop)
            Long l = HBList.get(i);
            if (l.compareTo(tenSecondsAgo) < 0) {// if too old
                HBList.remove(i);// remove and DO NOT increment i
            } else { // else, take into account and go to next (increment i)
                i++;
                sum++;
            }
        }
        currentHeartBeat = (int) ((double) sum / (double) (heartBeatAvgInterval) * 60.0);//Average on {heartBeatAvgInterval} seconds, that we put on a 60seconds basis
        if (DBG) {
            //System.out.println("currentHeartBeat=" + currentHeartBeat);
        }
    }

    /**
     * This method is executed in manageInput() everytime a heartbeat input from the player is received
     * @param delta
     */
    private void playerHeartBeat(int delta) {
        speedImpulse = new Vec2(computeImpulseFromHeartBeat(currentHeartBeat), 0.0f);
        playerBody.applyLinearImpulse(speedImpulse, playerBody.getPosition());
        java.util.Date date = new java.util.Date();
        HBList.add(date.getTime());// Adding the new HB to the list of HB from the player
    }
    
    public int getScore() {
        return score;
    }
    
    public float getLife() {
        return life;
    }
    
    public int getCurrentHeartBeat() {
        return currentHeartBeat;
    }
    
    public float getScoreModifier() {
        if (currentHeartBeat <= 0) {
            return 0;
        }
        else if (currentHeartBeat > 0 && currentHeartBeat <= GameParams.INSTANCE.LowBeatThreshold()) {
            return GameParams.INSTANCE.ScoreModifierLow();
        }
        else if (currentHeartBeat >= GameParams.INSTANCE.HighBeatThreshold()) {
            return GameParams.INSTANCE.ScoreModifierHigh();
        }
        else {
            return GameParams.INSTANCE.ScoreModifierNormal();
        }
    }
    
    public void addLife(int dLife) {
        life = life + dLife < 0 ? 0 : life + dLife;   
        setChanged();
        notifyObserver(lifeDisplay);
    }
    
    public void addScore(int dScore) {
        score += dScore;
        setChanged();
        notifyObserver(scoreDisplay);
        System.out.println("+Score: " + score);
    }
    
    // --- Observer methods

    @Override
    public void addObserver(IObserver o) {
        observers.add(o);
    }

    @Override
    public int countObservers() {
        return observers.size();
    }

    @Override
    public void deleteObserver(IObserver o) {
        observers.remove(o);
    }

    @Override
    public void deleteObservers(IObserver o) {
        observers.clear();
    }

    @Override
    public boolean hasChanged() {
        return hasChanged;
    }
    
    @Override
    public void notifyObserver(IObserver o) {
        if (hasChanged() && observers.indexOf(o) != -1) {
            observers.get(observers.indexOf(o)).update(this, null);
            clearChanged();
        }
    }
    
    @Override
    public void notifyObservers() {
        if (hasChanged()) {
            for (IObserver obs : observers) {
                obs.update(this, null);
            }
            clearChanged();
        }
    }
    
    protected void clearChanged() {
        hasChanged = false;
    }
    
    protected void setChanged() {
        hasChanged = true;
    }
}
