package de.konradvoelkel.android.autokorrektur

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import de.konradvoelkel.android.autokorrektur.databinding.ActivityMainBinding
import de.konradvoelkel.android.autokorrektur.ui.compose.AboutDialog
import de.konradvoelkel.android.autokorrektur.ui.compose.AppDialog
import de.konradvoelkel.android.autokorrektur.ui.compose.AutoKorrekturTheme
import de.konradvoelkel.android.autokorrektur.ui.compose.DiagnosticsDialog

/**
 * Main application host activity managing the toolbar, navigation graph, and global option menus.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    /**
     * Which global dialog is open. The toolbar and the navigation host are still Views; only the
     * dialogs have moved to Compose so far, hosted by the zero-sized `dialogHost` ComposeView.
     */
    private var openDialog by mutableStateOf(AppDialog.NONE)

    /**
     * Initializes activity layout, toolbar, and Android Jetpack Navigation host fragment.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.dialogHost.setContent {
            AutoKorrekturTheme {
                when (openDialog) {
                    AppDialog.ABOUT -> AboutDialog(onDismiss = { openDialog = AppDialog.NONE })
                    AppDialog.DIAGNOSTICS -> DiagnosticsDialog(onDismiss = { openDialog = AppDialog.NONE })
                    AppDialog.NONE -> Unit
                }
            }
        }
    }

    /**
     * Inflates the top-level app options menu containing About and settings actions.
     */
    override fun onCreateOptionsMenu(optionsMenu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, optionsMenu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_about -> {
                openDialog = AppDialog.ABOUT
                true
            }
            R.id.action_diagnostics -> {
                openDialog = AppDialog.DIAGNOSTICS
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
