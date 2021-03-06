package com.settlers;

import com.settlers.Board.Cards;
import com.settlers.Slate.Action;
import com.settlers.UIButton.Type;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.Toast;

public class Game extends Activity {

	private static final int MIN_BOT_DELAY = 1000;

	private static final int UPDATE_MESSAGE = 1, LOG_MESSAGE = 2,
			DISCARD_MESSAGE = 3;

	private Slate slate;
	private Board board;

	private Handler turnHandler;
	private TurnThread turnThread;

	private boolean isActive;

	private static final String[] ROLLS = { "", "⚀", "⚁", "⚂", "⚃", "⚄", "⚅" };

	class TurnThread implements Runnable {
		private boolean done;

		@Override
		public void run() {
			done = false;

			while (!done) {
				if (board.getWinner(null) != null)
					return;

				if (board.checkPlayerToDiscard()) {
					Message discard = new Message();
					discard.what = DISCARD_MESSAGE;
					turnHandler.sendMessage(discard);
				} else if (board.getCurrentPlayer().isBot()) {
					board.runTurn();
					Message change = new Message();
					change.what = UPDATE_MESSAGE;
					turnHandler.sendMessage(change);

					if (board.getCurrentPlayer().isHuman()) {
						Message turn = new Message();
						turn.what = LOG_MESSAGE;
						turnHandler.sendMessage(turn);
					}

					int delay = Options.turnDelay();
					if (delay > 0) {
						try {
							Thread.sleep(delay);
						} catch (InterruptedException e) {
							return;
						}
					}

					continue;
				}

				try {
					Thread.sleep(MIN_BOT_DELAY);
				} catch (InterruptedException e) {
					return;
				}
			}
		}

		public void end() {
			done = true;
		}
	}

	class UpdateHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case UPDATE_MESSAGE:
				setup(false);
				slate.invalidate();
				break;

			case LOG_MESSAGE:
				notifyTurn();
				break;

			case DISCARD_MESSAGE:
				if (board == null)
					return;

				Player toDiscard = board.getPlayerToDiscard();
				int cards = toDiscard.getResourceCount();
				int extra = cards > 7 ? cards / 2 : 0;

				if (extra == 0)
					break;

				Intent intent = new Intent(Game.this, Discard.class);
				intent.setClassName("com.settlers", "com.settlers.Discard");
				intent.putExtra(Discard.PLAYER_KEY, toDiscard.getIndex());
				intent.putExtra(Discard.QUANTITY_KEY, extra);
				Game.this.startActivity(intent);
				break;
			}

			super.handleMessage(msg);
		}
	}

	public void select(Action action, Hexagon hexagon) {
		if (action == Action.ROBBER) {
			if (hexagon != board.getRobberLast()) {
				board.setRobber(hexagon.getId());
				setup(false);
			} else {
				popup(getString(R.string.game_robber_fail),
						getString(R.string.game_robber_same));
			}
		}
	}

	public void select(Action action, Vertex vertex) {
		int type = Vertex.NONE;
		if (action == Action.TOWN)
			type = Vertex.TOWN;
		else if (action == Action.CITY)
			type = Vertex.CITY;

		Player player = board.getCurrentPlayer();
		if (player.build(vertex, type)) {
			if (board.isSetupTown())
				board.nextPhase();

			setup(false);
		}
	}

	public void select(Action action, Edge edge) {
		Player player = board.getCurrentPlayer();
		if (player.build(edge)) {
			slate.setAction(Action.NONE);

			if (board.isSetupRoad()) {
				board.nextPhase();
				setup(true);
			} else if (board.isProgressPhase()) {
				board.nextPhase();
				setup(false);
			} else {
				setup(false);
			}
		}
	}

	public boolean buttonPress(Type button) {
		switch (button) {
		case INFO:
			Game.this.startActivity(new Intent(Game.this, Status.class));
			break;

		case ROLL:
			// enter build phase
			board.nextPhase();

			int roll1 = (int) (Math.random() * 6) + 1;
			int roll2 = (int) (Math.random() * 6) + 1;
			int roll = roll1 + roll2;
			board.getCurrentPlayer().roll(roll);

			if (roll == 7) {
				toast(getString(R.string.game_rolled) + " 7 " + ROLLS[roll1]
						+ ROLLS[roll2] + " "
						+ getString(R.string.game_move_robber));
				setup(true);
				break;
			} else {
				toast(getString(R.string.game_rolled) + " " + roll + " "
						+ ROLLS[roll1] + ROLLS[roll2]);
			}

			setup(false);
			break;

		case ROAD:
			if (board.getCurrentPlayer().getNumRoads() >= Player.MAX_ROADS) {
				popup(getString(R.string.game_build_fail),
						getString(R.string.game_build_road_max));
				break;
			}

			slate.setAction(Action.ROAD);
			setButtons(Action.ROAD);
			this.setTitle(board.getCurrentPlayer().getName() + ": "
					+ getString(R.string.game_build_road));
			break;

		case TOWN:
			if (board.getCurrentPlayer().getNumTowns() >= Player.MAX_TOWNS) {
				popup(getString(R.string.game_build_fail),
						getString(R.string.game_build_town_max));
				break;
			}

			slate.setAction(Action.TOWN);
			setButtons(Action.TOWN);
			this.setTitle(board.getCurrentPlayer().getName() + ": "
					+ getString(R.string.game_build_town));
			break;

		case CITY:
			if (board.getCurrentPlayer().getNumCities() >= Player.MAX_CITIES) {
				popup(getString(R.string.game_build_fail),
						getString(R.string.game_build_city_max));
				break;
			}

			slate.setAction(Action.CITY);
			setButtons(Action.CITY);
			this.setTitle(board.getCurrentPlayer().getName() + ": "
					+ getString(R.string.game_build_city));
			break;

		case DEVCARD:
			development();
			break;

		case TRADE:
			this.startActivity(new Intent(this, PlayerTrade.class));
			setup(false);
			break;

		case ENDTURN:
			board.nextPhase();
			board.save(Settlers.getInstance().getSettingsInstance());
			setup(true);
			break;

		case CANCEL:
			// return false if there is nothing to cancel
			boolean result = slate.cancel();

			setup(false);
			return result;
		}

		return true;
	}

	public boolean clickResource(int index) {
		if (!board.getCurrentPlayer().isHuman() || !board.isBuild())
			return false;

		Intent intent = new Intent(this, PlayerTrade.class);
		intent.setClassName("com.settlers", "com.settlers.PlayerTrade");
		intent.putExtra(PlayerTrade.TYPE_KEY, index);
		startActivity(intent);

		return true;
	}

	public void cantBuild(Action action) {
		Board board = ((Settlers) getApplicationContext()).getBoardInstance();
		Player player = board.getCurrentPlayer();

		String message = "";
		switch (action) {
		case ROAD:

			if (player.getNumRoads() == Player.MAX_ROADS)
				message = getString(R.string.game_build_road_max);
			else
				message = getString(R.string.game_build_road_fail);

			if (board.isProgressPhase1()) {
				message += " " + getString(R.string.game_build_prog1_fail);
				board.getCurrentPlayer().addCard(Cards.PROGRESS, true);
				board.nextPhase();
				board.nextPhase();
			} else if (board.isProgressPhase2()) {
				message += " " + getString(R.string.game_build_prog2_fail);
				board.nextPhase();
			}

			break;

		case TOWN:
			buttonPress(Type.CANCEL);

			if (player.getNumTowns() == Player.MAX_TOWNS)
				message = getString(R.string.game_build_town_max);
			else
				message = getString(R.string.game_build_town_fail);

			break;

		case CITY:
			buttonPress(Type.CANCEL);

			if (player.getNumCities() == Player.MAX_CITIES)
				message = getString(R.string.game_build_city_max);
			else
				message = getString(R.string.game_build_city_fail);

			break;
		}

		popup(getString(R.string.game_build_fail), message);
		setup(false);
	}

	private void setup(boolean setZoom) {
		Settlers app = (Settlers) getApplicationContext();
		TextureManager texture = app.getTextureManagerInstance();
		Player player = board.getCurrentPlayer();

		slate.setState(this, board, player.isHuman() ? player : null, texture,
				board.getRoll());

		if (setZoom)
			slate.unZoom();

		// show card stealing dialog
		if (board.isRobberPhase() && board.getRobber() != null)
			steal();

		// display winner
		boolean hadWinner = board.getWinner(null) != null;
		Player winner = board.getWinner(((Settlers) getApplicationContext())
				.getSettingsInstance());
		if (!hadWinner && winner != null) {
			// clear saved game
			board.clear(Settlers.getInstance().getSettingsInstance());

			// declare winner
			final Builder infoDialog = new AlertDialog.Builder(this);
			infoDialog.setTitle(getString(R.string.phase_game_over));
			infoDialog.setIcon(R.drawable.icon);
			infoDialog.setMessage(winner.getName() + " "
					+ getString(R.string.game_won));
			infoDialog.setNeutralButton(getString(R.string.game_see_board),
					null);
			infoDialog.setPositiveButton(getString(R.string.game_return_menu),
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Game.this.finish();
						}
					});
			infoDialog.show();
		}

		if (board.isSetupTown())
			slate.setAction(Action.TOWN);
		else if (board.isSetupRoad() || board.isProgressPhase())
			slate.setAction(Action.ROAD);
		else if (board.isRobberPhase() && board.getRobber() == null)
			slate.setAction(Action.ROBBER);
		else
			slate.setAction(Action.NONE);

		setButtons(slate.getAction());

		setTitleColor(Color.WHITE);

		View titleView = getWindow().findViewById(android.R.id.title);
		if (titleView != null) {
			ViewParent parent = titleView.getParent();
			if (parent != null && (parent instanceof View)) {
				View parentView = (View) parent;
				int color = TextureManager.getColor(board.getCurrentPlayer()
						.getColor());
				color = TextureManager.darken(color, 0.5);
				parentView.setBackgroundColor(color);
			}
		}

		int resourceId = board.getPhaseResource();
		if (resourceId != 0)
			setTitle(board.getCurrentPlayer().getName() + ": "
					+ getString(resourceId));
		else
			setTitle(board.getCurrentPlayer().getName());

		slate.invalidate();
	}

	private void setButtons(Slate.Action action) {
		slate.removeButtons();

		slate.addButton(Type.INFO);

		Player player = board.getCurrentPlayer();
		Player winner = board.getWinner(null);

		if (winner != null || !player.isHuman()) {
			// anonymous mode
		} else if (board.isSetupPhase()) {
			// no extra buttons in setup phase
		} else if (board.isProgressPhase()) {
			// TODO: add ability to cancel card use
			// consider what happens if there's nowhere to build a road
		} else if (board.isRobberPhase()) {
			// do nothing
		} else if (action != Action.NONE) {
			// cancel the action
			slate.addButton(Type.CANCEL);
		} else if (board.isProduction()) {
			slate.addButton(Type.ROLL);

			if (player.canUseCard())
				slate.addButton(Type.DEVCARD);
		} else if (board.isBuild()) {
			slate.addButton(Type.TRADE);
			slate.addButton(Type.ENDTURN);

			if (player.affordCard() || player.canUseCard())
				slate.addButton(Type.DEVCARD);

			if (player.affordRoad())
				slate.addButton(Type.ROAD);

			if (player.affordTown())
				slate.addButton(Type.TOWN);

			if (player.affordCity())
				slate.addButton(Type.CITY);
		}
	}

	private void development() {
		Player player = board.getCurrentPlayer();
		int[] cards = player.getCards();

		CharSequence[] list = new CharSequence[Board.Cards.values().length + 2];
		int index = 0;

		if (player.affordCard() && board.isBuild())
			list[index++] = getString(R.string.game_buy_dev);

		for (int i = 0; i < Board.Cards.values().length; i++) {
			Board.Cards type = Board.Cards.values()[i];
			if (!player.hasCard(type))
				continue;

			String quantity = (cards[i] > 1 ? " (" + cards[i] + ")" : "");

			if (type == Cards.SOLDIER)
				list[index++] = getString(R.string.game_use_soldier) + quantity;
			else if (type == Cards.PROGRESS)
				list[index++] = getString(R.string.game_use_progress)
						+ quantity;
			else if (type == Cards.HARVEST)
				list[index++] = getString(R.string.game_use_harvest) + quantity;
			else if (type == Cards.MONOPOLY)
				list[index++] = getString(R.string.game_use_monopoly)
						+ quantity;
		}

		list[index++] = getString(R.string.game_cancel);

		CharSequence[] items = new CharSequence[index];
		for (int i = 0; i < index; i++)
			items[i] = list[i];

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.game_dev_cards));
		builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				Player player = board.getCurrentPlayer();

				if (player.affordCard() && board.isBuild()) {
					// buy a card
					if (item == 0) {
						Board.Cards card = player.buyCard();
						if (card != null)
							toast(getString(R.string.game_bought)
									+ " "
									+ getString(Board
											.getCardStringResource(card)) + " "
									+ getString(R.string.game_card));
						else
							toast(getString(R.string.game_no_cards));

						setup(false);
						return;
					}

					item--;
				}

				// try to use a card
				for (int i = 0; i < Board.Cards.values().length; i++) {
					Board.Cards type = Board.Cards.values()[i];
					if (item > 0 && player.hasCard(type)) {
						item--;
					} else if (item == 0 && player.hasCard(type)) {
						if (type == Board.Cards.HARVEST) {
							harvest();
						} else if (type == Board.Cards.MONOPOLY) {
							monopoly();
						} else if (player.useCard(type)) {
							if (type == Board.Cards.SOLDIER) {
								toast(getString(R.string.game_used_soldier));
								setup(true);
							} else {
								toast(getString(R.string.game_used_progress));
								setup(false);
							}
						} else {
							toast(getString(R.string.game_card_fail));
						}

						return;
					}
				}
			}
		});

		builder.create().show();
	}

	private void monopoly() {
		CharSequence[] items = new CharSequence[Hexagon.TYPES.length];
		for (int i = 0; i < items.length; i++)
			items[i] = String.format(getString(R.string.game_monopoly_select),
					getString(Hexagon.getTypeStringResource(Hexagon.TYPES[i])));

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.game_monopoly_prompt));
		builder.setItems(items, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Player player = board.getCurrentPlayer();

				if (player.useCard(Board.Cards.MONOPOLY)) {
					int total = player.monopoly(Hexagon.TYPES[which]);
					toast(String.format(getString(R.string.game_used_monopoly),
							total));
					setup(false);
				} else {
					toast(getString(R.string.game_card_fail));
				}
			}
		});

		builder.create().show();
	}

	private void harvest() {
		CharSequence[] items = new CharSequence[Hexagon.TYPES.length];
		for (int i = 0; i < items.length; i++)
			items[i] = String.format(getString(R.string.game_harvest_select),
					getString(Hexagon.getTypeStringResource(Hexagon.TYPES[i])));

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.game_harvest_prompt));
		builder.setItems(items, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Player player = board.getCurrentPlayer();

				if (player.useCard(Board.Cards.HARVEST)) {
					player.harvest(Hexagon.TYPES[which], Hexagon.TYPES[which]);
					toast(getString(R.string.game_used_harvest));
					setup(false);
				} else {
					toast(getString(R.string.game_card_fail));
				}
			}
		});

		builder.create().show();
	}

	private void steal() {
		if (!board.isRobberPhase()) {
			Log.w(this.getClass().getName(),
					"shouldn't be calling steal() out of robber phase");
			return;
		}

		Hexagon robbing = board.getRobber();
		if (robbing == null) {
			Log.w(this.getClass().getName(),
					"shouldn't be calling steal() without robber location set");
			setup(false);
			return;
		}

		Player current = board.getCurrentPlayer();

		CharSequence[] list = new CharSequence[3];
		int index = 0;

		Player player = null;
		for (int i = 0; i < 4; i++) {
			player = board.getPlayer(i);

			// don't steal from self or players without a town/city
			if (player == current || !robbing.hasPlayer(player))
				continue;

			// add to list of players to steal from
			int count = player.getResourceCount();
			list[index++] = getString(R.string.game_steal_from) + " "
					+ player.getName() + " (" + count + " "
					+ getString(R.string.game_resources) + ")";
		}

		if (index == 0) {
			// nobody to steal from
			toast(getString(R.string.game_steal_fail));

			board.nextPhase();
			setup(false);
			return;
		} else if (index == 1) {
			// automatically steal if only one player is listed
			steal(0);
			return;
		}

		// create new list that is the right size
		CharSequence[] items = new CharSequence[index];
		for (int i = 0; i < index; i++)
			items[i] = list[i];

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.game_dev_cards));
		builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				steal(item);
			}
		});

		AlertDialog stealDialog = builder.create();
		stealDialog.setCancelable(false);
		stealDialog.show();
	}

	private void steal(int select) {
		if (!board.isRobberPhase())
			return;

		Player current = board.getCurrentPlayer();

		Hexagon robbing = board.getRobber();
		if (robbing == null)
			return;

		int index = 0;
		for (int i = 0; i < 4; i++) {
			Player player = board.getPlayer(i);
			if (player == current || !robbing.hasPlayer(player))
				continue;

			if (index == select) {
				Hexagon.Type type = board.getCurrentPlayer().steal(player);

				if (type != null)
					toast(getString(R.string.game_stole) + " "
							+ getString(Hexagon.getTypeStringResource(type))
							+ " " + getString(R.string.game_from) + " "
							+ player.getName());
				else
					toast(getString(R.string.game_player_steal_fail) + " "
							+ player.getName());

				board.nextPhase();
				setup(false);
				return;
			}

			index++;
		}
	}

	private void toast(String message) {
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT)
				.show();
	}

	private void popup(String title, String message) {
		final Builder infoDialog = new AlertDialog.Builder(this);
		infoDialog.setTitle(title);
		infoDialog.setIcon(R.drawable.icon);
		infoDialog.setMessage(message);
		infoDialog.setNeutralButton(getString(R.string.game_ok), null);
		infoDialog.show();
	}

	private void notifyTurn() {
		// vibrate if enabled
		Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		if (Options.vibrateTurn())
			vibrator.vibrate(400);

		// TODO: only works if "Audible selection" is enabled in Android
		if (Options.beepTurn())
			slate.playSoundEffect(SoundEffectConstants.CLICK);

		// show turn log
		if (Options.turnLog() && board.isProduction() && isActive)
			turnLog();
	}

	private void turnLog() {
		String message = "";

		// show log of the other players' turns
		int offset = board.getCurrentPlayer().getIndex() + 1;
		for (int i = offset; i < offset + 3; i++) {
			// don't include players after you on your first turn
			if (board.getTurnNumber() == 1 && (i % 4) >= offset)
				continue;

			Player player = board.getPlayer(i % 4);
			String name = player.getName()
					+ " ("
					+ getString(Player
							.getColorStringResource(player.getColor())) + ")";
			String log = player.getActionLog();

			if (message != "")
				message += "\n";

			if (log == null || log == "")
				message += name + " " + getString(R.string.game_did_nothing)
						+ "\n";
			else
				message += name + "\n" + log + "\n";
		}

		if (message != "")
			popup(getString(R.string.game_turn_log), message);
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.game);

		slate = (Slate) findViewById(R.id.game_view);
		slate.requestFocus();

		Settlers app = (Settlers) getApplicationContext();

		board = app.getBoardInstance();

		if (board == null) {
			Settings settings = ((Settlers) getApplicationContext())
					.getSettingsInstance();

			String[] names = new String[4];
			for (int i = 0; i < 4; i++)
				names[i] = getString(LocalGame.DEFAULT_NAMES[i]);

			board = new Board(names, LocalGame.DEFAULT_HUMANS, 10, false);

			if (board.load(settings))
				((Settlers) getApplicationContext()).setBoardInstance(board);
			else
				board = null;
		}

		if (board == null) {
			finish();
			return;
		}

		TextureManager texture = app.getTextureManagerInstance();
		if (texture == null) {
			texture = new TextureManager(getResources());
			app.setTextureManagerInstance(texture);
		}

		turnHandler = new UpdateHandler();
	}

	@Override
	public void onResume() {
		super.onResume();

		if (!Options.showStatus()) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		} else {
			getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		turnThread = new TurnThread();
		new Thread(turnThread).start();

		isActive = true;
		setup(false);
	}

	@Override
	public void onPause() {
		isActive = false;
		turnThread.end();
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.gamemenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.reference:
			Game.this.startActivity(new Intent(Game.this, Reference.class));
			return true;
		case R.id.status:
			Game.this.startActivity(new Intent(Game.this, Status.class));
			return true;
		case R.id.options:
			Game.this.startActivity(new Intent(Game.this, Options.class));
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// return to menu if there's nothing to cancel
			if (buttonPress(Type.CANCEL))
				slate.unZoom();
			else
				finish();

			return true;
		} else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
			// show status from search button
			Game.this.startActivity(new Intent(Game.this, Status.class));
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}
}
