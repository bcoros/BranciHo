-- ============================================================
-- JUMP BOOST OBBY - Paste this into Roblox Studio Command Bar
-- Every jump makes your JumpPower bigger! + Auto-built Obby
-- ============================================================

-- Clean up old obby if re-running
if workspace:FindFirstChild("JumpBoostObby") then
    workspace.JumpBoostObby:Destroy()
end

local ObbyFolder = Instance.new("Folder")
ObbyFolder.Name = "JumpBoostObby"
ObbyFolder.Parent = workspace

-- =====================
-- SETTINGS
-- =====================
local START_JUMP_POWER = 50
local JUMP_BOOST_PER_JUMP = 3
local MAX_JUMP_POWER = 200
local PLATFORM_COLOR = BrickColor.new("Bright green")
local LAVA_COLOR = BrickColor.new("Really red")
local FINISH_COLOR = BrickColor.new("Bright yellow")

-- =====================
-- BUILD THE OBBY
-- =====================
local function makePart(name, size, position, color, anchored, parent)
    local p = Instance.new("Part")
    p.Name = name
    p.Size = size
    p.Position = position
    p.BrickColor = color
    p.Anchored = anchored
    p.TopSurface = Enum.SurfaceType.Smooth
    p.BottomSurface = Enum.SurfaceType.Smooth
    p.Parent = parent or ObbyFolder
    return p
end

-- Spawn platform
local spawn = makePart("SpawnPlatform", Vector3.new(20, 2, 20), Vector3.new(0, 0, 0), BrickColor.new("Medium stone grey"), true)

-- SpawnLocation on the spawn platform
local spawnLoc = Instance.new("SpawnLocation")
spawnLoc.Size = Vector3.new(8, 1, 8)
spawnLoc.Position = Vector3.new(0, 1.5, 0)
spawnLoc.Anchored = true
spawnLoc.TopSurface = Enum.SurfaceType.Smooth
spawnLoc.BottomSurface = Enum.SurfaceType.Smooth
spawnLoc.BrickColor = BrickColor.new("White")
spawnLoc.Parent = ObbyFolder

-- Obby platforms layout: {offset X, offset Y, offset Z, sizeX, sizeZ}
local platforms = {
    {15, 0, 0, 8, 8},
    {28, 5, 5, 7, 7},
    {40, 8, -3, 6, 6},
    {52, 12, 4, 6, 6},
    {60, 18, 12, 5, 5},
    {68, 24, 4, 5, 5},
    {78, 28, -4, 6, 6},
    {88, 34, 5, 5, 5},
    {96, 40, 14, 5, 5},
    {106, 48, 6, 5, 5},
    {116, 55, -2, 6, 6},
    {128, 62, 6, 5, 5},
    {138, 70, 0, 5, 5},
    {148, 78, 8, 5, 5},
    {160, 86, 0, 8, 8},  -- finish platform (bigger)
}

for i, data in ipairs(platforms) do
    local x, y, z, sx, sz = data[1], data[2], data[3], data[4], data[5]
    local color = PLATFORM_COLOR
    local name = "Platform_" .. i
    if i == #platforms then
        color = FINISH_COLOR
        name = "FinishPlatform"
    end
    makePart(name, Vector3.new(sx, 2, sz), Vector3.new(x, y, z), color, true)
end

-- Lava kill bricks between some platforms for challenge
local lavaParts = {
    {20, -2, 0, 10, 2, 14},
    {45, 4, 0, 8, 1, 14},
    {74, 20, 0, 10, 1, 20},
    {102, 38, 10, 10, 1, 14},
    {134, 58, 3, 10, 1, 14},
}

for i, data in ipairs(lavaParts) do
    local lava = makePart("Lava_" .. i,
        Vector3.new(data[4], data[5], data[6]),
        Vector3.new(data[1], data[2], data[3]),
        LAVA_COLOR, true)
    lava.Material = Enum.Material.Neon
end

-- Decorative walls / guide rails on a couple platforms
makePart("Wall1", Vector3.new(1, 6, 8), Vector3.new(56, 15, 4), BrickColor.new("Dark stone grey"), true)
makePart("Wall2", Vector3.new(1, 6, 6), Vector3.new(82, 31, -4), BrickColor.new("Dark stone grey"), true)

-- Victory part on finish
local trophy = Instance.new("Part")
trophy.Name = "Trophy"
trophy.Shape = Enum.PartType.Ball
trophy.Size = Vector3.new(4, 4, 4)
trophy.Position = Vector3.new(160, 92, 0)
trophy.BrickColor = BrickColor.new("Bright yellow")
trophy.Material = Enum.Material.Neon
trophy.Anchored = true
trophy.Parent = ObbyFolder

-- Billboard on trophy
local bg = Instance.new("BillboardGui")
bg.Size = UDim2.new(0, 200, 0, 50)
bg.StudsOffset = Vector3.new(0, 4, 0)
bg.Adornee = trophy
bg.Parent = trophy
local tl = Instance.new("TextLabel")
tl.Size = UDim2.new(1, 0, 1, 0)
tl.BackgroundTransparency = 1
tl.Text = "YOU WIN!"
tl.TextColor3 = Color3.fromRGB(255, 255, 0)
tl.TextScaled = true
tl.Font = Enum.Font.GothamBold
tl.Parent = bg

-- =====================
-- JUMP BOOST SYSTEM (Server Script)
-- =====================
local jumpScript = Instance.new("Script")
jumpScript.Name = "JumpBoostScript"
jumpScript.Parent = ObbyFolder

jumpScript.Source = [==[
local Players = game:GetService("Players")

local START_JUMP_POWER = 50
local BOOST_PER_JUMP = 3
local MAX_JUMP_POWER = 200

local playerData = {}

Players.PlayerAdded:Connect(function(player)
    player.CharacterAdded:Connect(function(character)
        local humanoid = character:WaitForChild("Humanoid")

        -- Reset jump power on respawn but keep accumulated boost
        if not playerData[player.UserId] then
            playerData[player.UserId] = START_JUMP_POWER
        end

        humanoid.JumpPower = playerData[player.UserId]
        humanoid.UseJumpPower = true

        -- Listen for jumps
        humanoid.StateChanged:Connect(function(oldState, newState)
            if newState == Enum.HumanoidStateType.Jumping then
                local newPower = math.min(humanoid.JumpPower + BOOST_PER_JUMP, MAX_JUMP_POWER)
                humanoid.JumpPower = newPower
                playerData[player.UserId] = newPower
            end
        end)
    end)
end)

Players.PlayerRemoving:Connect(function(player)
    playerData[player.UserId] = nil
end)
]==]

-- =====================
-- LAVA KILL SCRIPT
-- =====================
local lavaScript = Instance.new("Script")
lavaScript.Name = "LavaKillScript"
lavaScript.Parent = ObbyFolder

lavaScript.Source = [==[
local ObbyFolder = script.Parent

for _, part in ipairs(ObbyFolder:GetChildren()) do
    if part:IsA("BasePart") and string.find(part.Name, "Lava") then
        part.Touched:Connect(function(hit)
            local humanoid = hit.Parent and hit.Parent:FindFirstChild("Humanoid")
            if humanoid and humanoid.Health > 0 then
                humanoid.Health = 0
            end
        end)
    end
end
]==]

-- =====================
-- FINISH / WIN SCRIPT
-- =====================
local winScript = Instance.new("Script")
winScript.Name = "WinScript"
winScript.Parent = ObbyFolder

winScript.Source = [==[
local trophy = script.Parent:WaitForChild("Trophy")

trophy.Touched:Connect(function(hit)
    local player = game:GetService("Players"):GetPlayerFromCharacter(hit.Parent)
    if player then
        -- Simple win message via a GUI
        local sg = player:FindFirstChild("PlayerGui")
        if sg and not sg:FindFirstChild("WinGui") then
            local gui = Instance.new("ScreenGui")
            gui.Name = "WinGui"
            gui.Parent = sg

            local frame = Instance.new("Frame")
            frame.Size = UDim2.new(0.5, 0, 0.3, 0)
            frame.Position = UDim2.new(0.25, 0, 0.35, 0)
            frame.BackgroundColor3 = Color3.fromRGB(0, 0, 0)
            frame.BackgroundTransparency = 0.3
            frame.Parent = gui

            local label = Instance.new("TextLabel")
            label.Size = UDim2.new(1, 0, 1, 0)
            label.BackgroundTransparency = 1
            label.Text = "YOU WIN! Your final JumpPower: " ..
                tostring(math.floor(hit.Parent:FindFirstChild("Humanoid").JumpPower))
            label.TextColor3 = Color3.fromRGB(255, 255, 0)
            label.TextScaled = true
            label.Font = Enum.Font.GothamBold
            label.Parent = frame

            game:GetService("Debris"):AddItem(gui, 5)
        end
    end
end)
]==]

-- =====================
-- JUMP POWER HUD (LocalScript via StarterGui)
-- =====================
local starterGui = game:GetService("StarterGui")

-- Clean up old GUI if re-running
if starterGui:FindFirstChild("JumpBoostHud") then
    starterGui.JumpBoostHud:Destroy()
end

local hud = Instance.new("ScreenGui")
hud.Name = "JumpBoostHud"
hud.ResetOnSpawn = false
hud.Parent = starterGui

local hudLabel = Instance.new("TextLabel")
hudLabel.Name = "JumpPowerLabel"
hudLabel.Size = UDim2.new(0, 250, 0, 50)
hudLabel.Position = UDim2.new(0.5, -125, 0, 10)
hudLabel.BackgroundColor3 = Color3.fromRGB(30, 30, 30)
hudLabel.BackgroundTransparency = 0.4
hudLabel.TextColor3 = Color3.fromRGB(100, 255, 100)
hudLabel.TextScaled = true
hudLabel.Font = Enum.Font.GothamBold
hudLabel.Text = "Jump Power: 50"
hudLabel.Parent = hud

local uiCorner = Instance.new("UICorner")
uiCorner.CornerRadius = UDim.new(0, 10)
uiCorner.Parent = hudLabel

local localScript = Instance.new("LocalScript")
localScript.Name = "HudUpdater"
localScript.Parent = hud

localScript.Source = [==[
local player = game:GetService("Players").LocalPlayer
local label = script.Parent:WaitForChild("JumpPowerLabel")

local function hookCharacter(character)
    local humanoid = character:WaitForChild("Humanoid")
    while humanoid and humanoid.Health > 0 do
        label.Text = "Jump Power: " .. tostring(math.floor(humanoid.JumpPower))
        task.wait(0.1)
    end
end

if player.Character then
    task.spawn(hookCharacter, player.Character)
end

player.CharacterAdded:Connect(function(char)
    task.spawn(hookCharacter, char)
end)
]==]

print("Jump Boost Obby loaded! Obby built with " .. #platforms .. " platforms.")
print("Jump starts at " .. START_JUMP_POWER .. ", gains +" .. JUMP_BOOST_PER_JUMP .. " per jump, max " .. MAX_JUMP_POWER)
