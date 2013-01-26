/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nanoblood;

import java.util.ArrayList;
import java.util.List;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;
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
public class GamePlay extends BasicGameState {

	int stateID = -1;
	Player player;
	LevelManager levelManager;
	List<StaticObject> objects;
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
	static public final int INITIAL_HEARTBEATS = 70;// per minutes
	private int HEARTBEAT_THRESHOLD_CRAZY = 150;// dying soon
	private int HEARTBEAT_THRESHOLD_MEDIUM = 90;// quite excited
	private int HEARTBEAT_THRESHOLD_HARD = 120;// runner
	private int heartBeatsSinceLastUpdate = 0;
	protected static final float PIXELS_TO_METERS_RATIO = 10.0f;
	static final int OBSTACLE_SPAWN_DELAY = 300; // delay in pixels
	private static boolean DBG = true;
	

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

		nextDistancePopObstacle = Main.width + deltaDistancePopObstacle;

		player.setCoords(200, Main.height / 2);

		for (int i = 0; i < 6; i++) {
			Obstacle o = Obstacle.getRandomObstacle();
			o.setCoords(i * 200, (int) (Math.random() * Main.height));
			this.objects.add(o);
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
                
System.out.println(this.player.getCoords());
	}

	@Override
	public void update(GameContainer gc, StateBasedGame sbg, int delta) throws SlickException {
		removeObjects();
		addObjects();

		manageInput(gc, sbg, delta);

		updateCurrentHeartBeats(delta);
		updatePhysics();
		updateObjects();

		this.levelManager.update(m2px(this.playerBody.getLinearVelocity().x));

		manageColisions();
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
//			this.bloodSpeed += this.bloodSpeedImpulse;
			heartBeatsSinceLastUpdate++;
			speedImpulse = new Vec2(computeImpulseFromHeartBeat(currentHeartBeat), 0.0f);
			playerBody.applyLinearImpulse(speedImpulse, playerBody.getPosition());
		} 

		totalDistance += bloodSpeed;//@TODO change that
	}

	private void updateObjects() {
		List<StaticObject> toRemove = new ArrayList<StaticObject>();

		for (StaticObject so : this.objects) {
			so.move((int) (- 1.0f * this.playerBody.getLinearVelocity().x / 3.0f), 0);

                        if(so instanceof Cancer) {
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

		// WIP : FX
//        if (bloodSpeed < 100) {
//            float alpha = 1 * (100 - bloodSpeed) / (100);
//            System.out.println(bloodSpeed + " -> alpha = " + alpha);
//            levelManager.setBlackFxAlpha(alpha);
//        }
	}

	private void manageColisions() {

		for (StaticObject so : this.objects) {
			if (this.player.boundingBox.intersects(so.getBoundingBox())) {
				so.colideWithPlayer();
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
//		for (int i = 0; i < 50; i++) {
//			PolygonShape shape = new PolygonShape();
//			shape.setAsBox(10.0f, 10.0f);
//			gndBody.createFixture(shape, 1.0f);
//		}
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

	private double heartBeatAvgInterval = 10.0; // In seconds
	private int heartBeatTimer = 0; // in "delta" units
	private void updateCurrentHeartBeats(int delta) {
		heartBeatTimer += delta;
		if ((heartBeatAvgInterval* 1000) <= heartBeatTimer) { // 1000 delta values = 1 second
			// Computing average:
			currentHeartBeat = (int) (heartBeatsSinceLastUpdate / heartBeatAvgInterval * 60.0);//Average on {heartBeatAvgInterval} seconds, that we put on a 60seconds basis
			// Resetting values:
			heartBeatTimer -= heartBeatAvgInterval*1000;
			heartBeatsSinceLastUpdate = 0;
			if (DBG) {
				System.out.println("currentHeartBeat=" + currentHeartBeat);
			}
		}
		
	}
}
