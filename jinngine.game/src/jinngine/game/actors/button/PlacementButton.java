package jinngine.game.actors.button;


import java.io.IOException;
import org.newdawn.slick.openal.*;

import com.ardor3d.image.Texture;
import com.ardor3d.image.TextureStoreFormat;
import com.ardor3d.math.Vector2;
import com.ardor3d.renderer.state.TextureState;
import com.ardor3d.renderer.state.RenderState.StateType;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.util.TextureManager;

import jinngine.game.Game;
import jinngine.game.actors.ActionActor;
import jinngine.game.actors.Actor;
import jinngine.game.actors.ActorOwner;
import jinngine.game.actors.PhysicalActor;
import jinngine.game.actors.interaction.BodyPlacement;
import jinngine.game.actors.player.Player;

import jinngine.physics.Body;


public class PlacementButton extends Button {
	
	private Texture selectedtexture;
	private Texture deselectedtexture;	
	private TextureState texturestate;
	private Player player = null;
	private final double distancelimit = 2.5;
	private Audio click;
	private int audiobufferid = 0;
	
	public PlacementButton() {
		
		try {
//			SoundStore.get().get
			click = SoundStore.get().getWAV("audiodump.wav");
			click.playAsSoundEffect(1, 0, false);
//			System.out.println(click.getPosition());
//			audiobufferid = click.getBufferID();

			System.out.println(click);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	@Override
	public void act(Game game) {
		super.act(game);
		
		//if we have no player, search for him
		if (player == null) {
			player = (Player)game.getRendering().getScene().getChild("Actor:Player");
		}
	}
	
	@Override
	public void start(Game game) {
		super.start(game);
		
		// load textures
		selectedtexture = TextureManager.load("selectedhand.tga",
				Texture.MinificationFilter.Trilinear,
				TextureStoreFormat.GuessNoCompressedFormat, true);

		deselectedtexture = TextureManager.load("deselectedhand.tga",
				Texture.MinificationFilter.Trilinear,
				TextureStoreFormat.GuessNoCompressedFormat, true);
		
		// get the texture state on the button box
		texturestate = (TextureState)buttonnode.getChild("mybuttonbox").getLocalRenderState(StateType.Texture);

		// set the deselected texture by default
		texturestate.setTexture(deselectedtexture);	
	}
	
	@Override
	public ActionActor provideActionActor(ActorOwner owner, Actor target, Node picknode,
			jinngine.math.Vector3 pickpoint, Vector2 screenpos ) {
				
		System.out.println("PlacementButton: got an actor "+ target);
		
		// spawn a BodyPlacement actor if possible
		if (target instanceof PhysicalActor) {
			PhysicalActor physactor = (PhysicalActor)target;
			Body body = physactor.getBodyFromNode(picknode);
		
			if (body != null)
				return new BodyPlacement(owner, body,pickpoint,screenpos);
			else
				System.out.println("No body recieved from actor");
		}
				
		return null;
	}
	
	@Override
	public void setSelected(Game game, boolean selected) {
		super.setSelected(game, selected);
		
		if (selected) {
			// play click sound
			click.playAsSoundEffect(1, 100, false);

			texturestate.setTexture(selectedtexture);
			
		} else {
			click.playAsSoundEffect(1, 100, false);

			// play click sound
			texturestate.setTexture(deselectedtexture);	
		}
	}
	
	@Override
	public boolean canBeSelected() {
		if (player !=null) {
			if (buttonbody.getPosition().minus(player.getPosition()).norm() > distancelimit ) {
				return false;
			} else {
				return true;
			}
		}
		
		return false;
	}
	
}
